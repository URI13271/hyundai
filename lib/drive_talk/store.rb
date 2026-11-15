# frozen_string_literal: true

require "securerandom"

module DriveTalk
  # 쿠키 하나의 세션. 요청 스레드가 같은 판을 나눠 가진다.
  # DRIVE_TALK_API가 있으면 판은 API가 갖고, 없으면 이 프로세스 안 목 세션이다.
  module Store
    LOCK = Mutex.new
    @games = {}

    def self.with(id)
      LOCK.synchronize do
        id = SecureRandom.hex(8) if id.nil? || id.empty?
        game = @games[id] ||= (Api.on? ? RemoteGame : Game).new(id)
        yield game
      end
    end
  end
end
