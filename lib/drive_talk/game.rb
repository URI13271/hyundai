# frozen_string_literal: true

module DriveTalk
  Seat = Data.define(:id, :name, :driver) do
    def self.row
      [
        new("driver", "운전석", true),
        new("passenger", "조수석", false),
        new("rear_left", "뒷좌석 왼쪽", false),
        new("rear_right", "뒷좌석 오른쪽", false)
      ]
    end
  end

  # 한 판의 화면 상태. 프로세스 밖으로는 나가지 않는다.
  class Game
    attr_reader :id, :screen, :index, :deck, :log, :notice

    def initialize(id)
      @id = id
      @screen = :splash
      @mic = nil
      @joined = Seat.row.map(&:id)
      @picks = {}
      @index = 0
      @deck = []
      @log = []
      @fresh = nil
      @leave = false
      @notice = nil
    end

    def leave? = @leave
    def mic? = @mic != false
    def question = @deck[@index]
    def last? = !@deck.empty? && @index >= @deck.length - 1
    def progress = @deck.empty? ? 0 : ((@index + 1) * 100.0 / @deck.length)
    def joined_count = @joined.length

    def seats
      Seat.row.map do |seat|
        choice = @picks[seat.id]
        {
          id: seat.id, name: seat.name, driver: seat.driver,
          joined: @joined.include?(seat.id),
          choice: choice,
          speaking: @fresh == seat.id,
          turn: choice.nil? && seat.id == current_id
        }
      end
    end

    def joined_seats = seats.select { |s| s[:joined] }

    def counts
      {
        a: @picks.count { |id, c| @joined.include?(id) && c == :a },
        b: @picks.count { |id, c| @joined.include?(id) && c == :b }
      }
    end

    def current_id
      @joined.find { |id| @picks[id].nil? }
    end

    def current
      seat = Seat.row.find { |item| item.id == current_id }
      return unless seat

      { id: seat.id, name: seat.name }
    end

    def consume_fresh
      id = @fresh
      @fresh = nil
      id
    end

    def advance_from_splash
      @screen = :home if @screen == :splash
    end

    def show_mic
      return if %i[shuffle seats read answer].include?(@screen)

      @leave = false
      @screen = :mic
    end

    def grant_mic
      @mic = true
      start_shuffle
    end

    def start
      return show_mic unless mic?

      start_shuffle
    end

    def finish_shuffle
      @screen = :seats if @screen == :shuffle
    end

    def toggle(seat_id)
      return unless @screen == :seats && Seat.row.any? { |s| s.id == seat_id }

      @notice = nil
      if @joined.include?(seat_id)
        @joined.delete(seat_id)
      else
        @joined << seat_id
      end
    end

    def begin_round
      return unless @screen == :seats

      if @joined.empty?
        @notice = "한 자리는 참여해야 시작할 수 있어요."
        return
      end

      @notice = nil
      @picks = {}
      @fresh = nil
      @screen = :read
    end

    def finish_read
      @screen = :answer if @screen == :read
    end

    def hear(choice)
      return unless @screen == :answer && %w[a b].include?(choice.to_s)
      return unless (id = current_id)

      @picks[id] = choice.to_sym
      @fresh = id
    end

    def replay
      return unless @screen == :answer

      @fresh = nil
      @screen = :read
    end

    # 06의 다음은 활성. 아직 고르지 않은 참여 좌석은 건너뛴다.
    def next_step
      return unless @screen == :answer

      @log << snapshot
      @fresh = nil
      if last?
        @screen = :done
      else
        @index += 1
        @picks = {}
        @screen = :read
      end
    end

    def ask_leave
      @leave = true if %i[shuffle seats read answer].include?(@screen)
    end

    def stay
      @leave = false
    end

    def quit
      reset_match
      @screen = :home
    end

    def again
      reset_match
      start_shuffle
    end

    def home
      reset_match
      @screen = :home
    end

    def tally
      {
        a: @log.count { |r| r[:a] > r[:b] },
        b: @log.count { |r| r[:b] > r[:a] },
        rounds: @log
      }
    end

    private

    def start_shuffle
      @deck = Deck.deal
      @index = 0
      @picks = {}
      @log = []
      @fresh = nil
      @leave = false
      @notice = nil
      @joined = Seat.row.map(&:id)
      @screen = :shuffle
    end

    def reset_match
      @deck = []
      @index = 0
      @picks = {}
      @log = []
      @fresh = nil
      @leave = false
      @notice = nil
      @joined = Seat.row.map(&:id)
    end

    def snapshot
      q = question
      c = counts
      { tag: q.tag, title: q.title, a_label: q.a, b_label: q.b, a: c[:a], b: c[:b] }
    end
  end
end
