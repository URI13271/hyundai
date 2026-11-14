# frozen_string_literal: true

require "erb"

module DriveTalk
  class View
    def initialize(game, theme:, layout:, demo:)
      @game = game
      @theme = theme
      @layout = layout
      @demo = demo
    end

    def render
      @fresh = @game.consume_fresh
      @fresh_seat = @game.seats.find { |s| s[:id] == @fresh }
      erb.result(binding)
    end

    def path = SCREEN_PATH.fetch(@game.screen)
    def pref(key, value) = "#{path}?#{key}=#{value}"

    def body_class
      [@layout == "auto" ? nil : @layout, @theme == "system" ? nil : @theme].compact.join(" ")
    end

    def header? = @game.screen != :splash

    def back_action
      @game.screen == :mic ? "/home" : "/leave"
    end

    def pill
      n = @game.joined_count
      if @fresh_seat
        ["outline", "#{@fresh_seat[:name]} 목소리 확인 중…"]
      elsif @game.screen == :read
        ["read", "질문 읽는 중… · #{n}명 참여"]
      elsif (seat = @game.current)
        ["live", "#{seat[:name]} 차례 · A? B? 말해 주세요"]
      else
        ["quiet", "모두 골랐어요"]
      end
    end

    def locked? = @game.screen == :read || @fresh

    def seat_class(seat)
      return "is-out" unless seat[:joined]
      return "is-fresh" if seat[:id] == @fresh
      return "is-a" if seat[:choice] == :a
      return "is-b" if seat[:choice] == :b
      return "is-turn" if seat[:turn] && @game.screen == :answer
      return "is-wait" if @game.screen == :read

      "is-in"
    end

    def seat_state(seat)
      return "미참여" unless seat[:joined]
      return "말하는 중" if seat[:id] == @fresh
      return "A 선택" if seat[:choice] == :a
      return "B 선택" if seat[:choice] == :b
      return "차례예요" if seat[:turn] && @game.screen == :answer
      return "대기 중" if @game.screen == :read

      "참여"
    end

    def hot?(count) = count.to_i.positive?

    def score_line(tally)
      return "오늘은 비겼어요" if tally[:a] == tally[:b]
      return "A가 #{tally[:a]}장으로 앞섰어요" if tally[:a] > tally[:b]

      "B가 #{tally[:b]}장으로 앞섰어요"
    end

    def on?(key, value)
      (key == :theme ? @theme : @layout) == value
    end

    private

    def erb
      @erb ||= ERB.new(File.read(File.join(ROOT, "views/screen.erb")), trim_mode: "-")
    end
  end
end
