# frozen_string_literal: true

module DriveTalk
  # 홈의 01 여행 카드는 피그마 시안 그대로. 실제 10장은 시작 때 셔플한다.
  Deck = Data.define(:tag, :title, :a, :b) do
    def self.sample
      all.first
    end

    def self.deal
      all.shuffle
    end

    def self.all
      [
        new("여행", "여행을 간다면 어디로?", "산", "바다"),
        new("음악", "차 안에서는 어떤 노래?", "잔잔한 곡", "신나는 곡"),
        new("야식", "지금 당장 야식은?", "치킨", "피자"),
        new("휴식", "쉬고 싶다면?", "집콕", "나들이"),
        new("아침", "아침은 무엇으로?", "커피", "차"),
        new("계절", "더 좋은 계절은?", "여름", "겨울"),
        new("길", "장거리는 어느 쪽?", "고속도로", "국도"),
        new("간식", "간식은 어느 쪽?", "달콤한 것", "짠 것"),
        new("영화", "영화 한 편이면?", "액션", "로맨스"),
        new("도착", "도착하면 먼저?", "밥", "잠")
      ]
    end
  end
end
