# frozen_string_literal: true

# 드라이브 토크 프론트. 화면과 목 세션만 가진다.
# 덱 제공, 음성 인식, 기록 저장의 주인은 BACKEND.md.
module DriveTalk
  ROOT = File.expand_path("..", __dir__)
  VERSION = "1.0.0"

  SCREEN_PATH = {
    splash: "/",
    home: "/home",
    mic: "/mic",
    shuffle: "/shuffle",
    seats: "/seats",
    read: "/question",
    answer: "/question",
    done: "/done"
  }.freeze
end

require_relative "drive_talk/deck"
require_relative "drive_talk/game"
require_relative "drive_talk/store"
require_relative "drive_talk/view"
require_relative "drive_talk/server"
