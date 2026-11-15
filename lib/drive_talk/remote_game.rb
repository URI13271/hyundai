# frozen_string_literal: true

module DriveTalk
  # 판의 주인이 API인 Game. 덱, 장, 좌석, 선택, 기록은 API 응답을 그대로 받아 적는다.
  # 스플래시, 마이크 안내, 나가기 확인, 목소리 확인 표시는 이 프로세스에만 있다.
  class RemoteGame < Game
    SID = /\A[0-9a-f]{8}\z/

    attr_reader :sid

    def initialize(id)
      super
      @sid = nil
    end

    # 요청마다 한 번. 쿠키의 판 id는 화면 서버가 다시 켜졌을 때만 쓴다.
    def sync(cookie_sid)
      adopted = @sid.nil?
      @sid ||= cookie_sid if SID.match?(cookie_sid.to_s)
      return unless @sid

      absorb(Api.get(path))
    rescue Api::Refused
      adopted ? @sid = nil : forget
    end

    def finish_shuffle
      act("ready") if @screen == :shuffle
    end

    def finish_read
      act("ready") if @screen == :read
    end

    def toggle(seat_id)
      return unless @screen == :seats && Seat.row.any? { |s| s.id == seat_id }

      @notice = nil
      act("seats", id: seat_id)
    end

    def begin_round
      return unless @screen == :seats

      @notice = nil
      @fresh = nil
      @notice = "한 자리는 참여해야 시작할 수 있어요." if act("begin") == 422
    end

    def hear(choice)
      return unless @screen == :answer && %w[a b].include?(choice.to_s)
      return unless (id = current_id)

      @fresh = id if act("hear", choice: choice.to_s) == 200
    end

    def replay
      return unless @screen == :answer

      @fresh = nil
      act("replay")
    end

    def next_step
      return unless @screen == :answer

      @fresh = nil
      act("next")
    end

    private

    def path(step = nil) = ["/v1/sessions", @sid, step].compact.join("/")

    # 거절은 화면을 바꾸지 않는다. 판이 없어졌으면 홈으로 간다.
    def act(step, body = nil)
      return 404 unless @sid

      absorb(Api.post(path(step), body))
      200
    rescue Api::Refused => e
      forget if e.status == 404
      e.status
    end

    def absorb(view)
      @sid = view.fetch("id")
      @screen = view.fetch("screen").to_sym
      @index = view.fetch("index")
      @deck = view.fetch("deck").map { |c| Deck.new(c["tag"], c["title"], c["a"], c["b"]) }
      seats = view.fetch("seats")
      @joined = seats.select { |s| s["joined"] }.map { |s| s["id"] }
      @picks = seats.each_with_object({}) { |s, picks| picks[s["id"]] = s["choice"].to_sym if s["choice"] }
      @log = view.fetch("log").map { |row| row.transform_keys(&:to_sym) }
    end

    def forget
      @sid = nil
      reset_match
      @screen = :home
    end

    def start_shuffle
      reset_match
      absorb(Api.post("/v1/sessions"))
    rescue Api::Unreachable
      @screen = :home
      raise
    end

    def reset_match
      drop
      super
    end

    def drop
      return unless @sid

      Api.delete(path)
    rescue Api::Refused
      nil
    ensure
      @sid = nil
    end
  end
end
