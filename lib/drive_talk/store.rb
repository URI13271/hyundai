# frozen_string_literal: true

require "securerandom"

module DriveTalk
  # 쿠키 하나의 목 세션. 요청 스레드가 같은 판을 나눠 가진다.
  module Store
    LOCK = Mutex.new
    @games = {}

    def self.with(id)
      LOCK.synchronize do
        id = SecureRandom.hex(8) if id.nil? || id.empty?
        game = @games[id] ||= Game.new(id)
        yield game
      end
    end
  end
end
