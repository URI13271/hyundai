// 서버 없이 도는 드라이브 토크. lib/drive_talk/game.rb와 views/screen.erb를 그대로 옮겼다.
// 판은 이 기기의 localStorage에 남는다. 앱이나 헤드유닛을 다시 켜도 하던 판으로 돌아온다.
(function () {
  "use strict";

  var KEY = "drive-talk";
  var SEATS = [
    { id: "driver", name: "운전석", driver: true },
    { id: "passenger", name: "조수석", driver: false },
    { id: "rear_left", name: "뒷좌석 왼쪽", driver: false },
    { id: "rear_right", name: "뒷좌석 오른쪽", driver: false }
  ];
  var CARDS = window.DRIVE_TALK_DECK || [];
  var ROUND = 10;
  var MATCH = ["shuffle", "seats", "read", "answer"];

  var app = document.getElementById("app");
  var timers = [];
  var fresh = null;
  var state = load();

  // 판

  function seatIds() { return SEATS.map(function (seat) { return seat.id; }); }

  function blank() {
    return {
      screen: "splash", mic: null, joined: seatIds(), picks: {},
      index: 0, deck: [], log: [], leave: false, notice: null
    };
  }

  function load() {
    var start = blank();
    try {
      var saved = JSON.parse(window.localStorage.getItem(KEY));
      if (!saved) return start;
      var open = MATCH.concat("done").indexOf(saved.screen) >= 0;
      if (open && Array.isArray(saved.deck) && saved.deck.length && Array.isArray(saved.log)) return saved;
      start.mic = saved.mic === undefined ? null : saved.mic;
    } catch (e) {
      return blank();
    }
    return start;
  }

  function save() {
    try { window.localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* 저장 못 해도 판은 이어 간다 */ }
  }

  function inMatch() { return MATCH.indexOf(state.screen) >= 0; }
  function joined(id) { return state.joined.indexOf(id) >= 0; }
  function question() { return state.deck[state.index]; }
  function last() { return state.deck.length > 0 && state.index >= state.deck.length - 1; }

  function currentId() {
    for (var i = 0; i < state.joined.length; i++) {
      if (!state.picks[state.joined[i]]) return state.joined[i];
    }
    return null;
  }

  function counts() {
    var out = { a: 0, b: 0 };
    state.joined.forEach(function (id) {
      if (state.picks[id]) out[state.picks[id]] += 1;
    });
    return out;
  }

  function seats() {
    var turn = currentId();
    return SEATS.map(function (seat) {
      var choice = state.picks[seat.id] || null;
      return {
        id: seat.id, name: seat.name, driver: seat.driver, joined: joined(seat.id),
        choice: choice, turn: !choice && seat.id === turn
      };
    });
  }

  function shuffled(cards) {
    var deck = cards.slice();
    for (var i = deck.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var held = deck[i];
      deck[i] = deck[j];
      deck[j] = held;
    }
    return deck.slice(0, ROUND);
  }

  function resetMatch() {
    state.deck = [];
    state.index = 0;
    state.picks = {};
    state.log = [];
    state.leave = false;
    state.notice = null;
    state.joined = seatIds();
    fresh = null;
  }

  function startShuffle() {
    resetMatch();
    state.deck = shuffled(CARDS);
    state.screen = "shuffle";
  }

  function showMic() {
    if (inMatch()) return;
    state.leave = false;
    state.screen = "mic";
  }

  var ACTIONS = {
    "/enter": function () { if (state.screen === "splash") state.screen = "home"; },
    "/start": function () { if (state.mic === false) showMic(); else startShuffle(); },
    "/mic": showMic,
    "/mic/open": function () { state.mic = true; startShuffle(); },
    "/home": function () { resetMatch(); state.screen = "home"; },
    "/ready": function () {
      if (state.screen === "shuffle") state.screen = "seats";
      else if (state.screen === "read") state.screen = "answer";
    },
    "/seat": function (id) {
      if (state.screen !== "seats" || seatIds().indexOf(id) < 0) return;
      state.notice = null;
      var at = state.joined.indexOf(id);
      if (at >= 0) state.joined.splice(at, 1);
      else state.joined.push(id);
    },
    "/begin": function () {
      if (state.screen !== "seats") return;
      if (!state.joined.length) {
        state.notice = "한 자리는 참여해야 시작할 수 있어요.";
        return;
      }
      state.notice = null;
      state.picks = {};
      fresh = null;
      state.screen = "read";
    },
    "/hear": function (choice) {
      if (state.screen !== "answer" || (choice !== "a" && choice !== "b")) return;
      var id = currentId();
      if (!id) return;
      state.picks[id] = choice;
      fresh = id;
    },
    "/settle": function () {},
    "/replay": function () {
      if (state.screen !== "answer") return;
      fresh = null;
      state.screen = "read";
    },
    // 다음은 아직 고르지 않은 참여 좌석을 건너뛴다.
    "/next": function () {
      if (state.screen !== "answer") return;
      var q = question();
      var c = counts();
      state.log.push({ tag: q.tag, title: q.title, a_label: q.a, b_label: q.b, a: c.a, b: c.b });
      fresh = null;
      if (last()) {
        state.screen = "done";
      } else {
        state.index += 1;
        state.picks = {};
        state.screen = "read";
      }
    },
    "/leave": function () { if (inMatch()) state.leave = true; },
    "/stay": function () { state.leave = false; },
    "/quit": function () { resetMatch(); state.screen = "home"; },
    "/again": startShuffle
  };

  function act(path, value) {
    var step = ACTIONS[path];
    if (!step) return;
    step(value);
    render();
  }

  // 화면

  var LOGO = '<rect x="8" y="16" width="30" height="22" rx="8" fill="#F08A3E"/><path d="M16 38 L14 48 L28 38 Z" fill="#F08A3E"/>' +
    '<rect x="26" y="24" width="28" height="20" rx="8" fill="#4C8DFF"/><path d="M46 44 L50 52 L36 44 Z" fill="#4C8DFF"/>';
  var MIC = '<svg class="mic" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v5a3 3 0 0 0 3 3zm5.5-3a5.5 5.5 0 0 1-11 0H5a7 7 0 0 0 6.25 6.93V21h1.5v-3.07A7 7 0 0 0 19 11h-1.5z"/></svg>';
  var WAVE = '<span class="wave" aria-hidden="true"><i></i><i></i><i></i><i></i></span>';

  function esc(text) {
    return String(text).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function logo(size, cls) {
    return '<div class="' + cls + '" aria-hidden="true"><svg viewBox="0 0 64 64" width="' + size + '" height="' + size + '">' + LOGO + "</svg></div>";
  }

  function form(action, inner, cls) {
    return '<form action="' + action + '"' + (cls ? ' class="' + cls + '"' : "") + ">" + inner + "</form>";
  }

  function header() {
    if (state.screen === "splash") return "";
    var back = "";
    if (state.screen === "mic" || inMatch()) {
      back = form(state.screen === "mic" ? "/home" : "/leave",
        '<button class="back" aria-label="뒤로"><svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M14.5 5.5 L8 12 L14.5 18.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></button>');
    }
    return '<header class="top">' + back + '<p class="brand">드라이브 토크</p></header>';
  }

  function seatClass(seat, freshId) {
    if (!seat.joined) return "is-out";
    if (seat.id === freshId) return "is-fresh";
    if (seat.choice === "a") return "is-a";
    if (seat.choice === "b") return "is-b";
    if (seat.turn && state.screen === "answer") return "is-turn";
    if (state.screen === "read") return "is-wait";
    return "is-in";
  }

  function seatState(seat, freshId) {
    if (!seat.joined) return "미참여";
    if (seat.id === freshId) return "말하는 중";
    if (seat.choice === "a") return "A 선택";
    if (seat.choice === "b") return "B 선택";
    if (seat.turn && state.screen === "answer") return "차례예요";
    if (state.screen === "read") return "대기 중";
    return "참여";
  }

  function seatCells(tag, freshId) {
    return seats().map(function (seat) {
      var attrs = tag === "button" ? ' name="id" value="' + seat.id + '"' : "";
      return "<" + tag + ' class="seat ' + seatClass(seat, freshId) + '"' + attrs + ">" +
        (seat.driver ? '<span class="drive-mark" aria-hidden="true">+</span>' : "") +
        "<b>" + esc(seat.name) + "</b><small>" + seatState(seat, freshId) + "</small></" + tag + ">";
    }).join("");
  }

  function splash() {
    return '<section class="center">' + logo(64, "logo") +
      "<h1>드라이브 토크</h1><p>차 안 취향 저울, 시작해 볼까요?</p></section>";
  }

  function home() {
    var q = CARDS[0];
    return '<section class="home"><div class="copy">' + logo(48, "logo sm") +
      "<h2>밸런스게임</h2>" +
      '<p class="sub">둘 중 하나, 말로 골라요.<br>누가 뭘 골랐는지 파란별로 읽어 줄게요.</p>' +
      '<div class="chips"><span class="chip">#질문카드 10장</span><span class="chip">#최대 4명</span></div>' +
      form("/start", '<button class="btn dark">게임 시작</button>') +
      '<p class="voice">' + MIC + " “게임 시작” 이라고 말해도 돼요</p></div>" +
      '<div class="preview"><article class="qcard">' +
      '<p class="qmeta">Q1 · #' + esc(q.tag) + "</p>" +
      '<h3 class="qtitle">' + esc(q.title) + "</h3>" +
      '<div class="options"><div class="opt opt-a"><span class="badge">A</span>' + esc(q.a) + "</div>" +
      '<span class="vs">VS</span><div class="opt opt-b"><span class="badge">B</span>' + esc(q.b) + "</div></div>" +
      '</article><p class="stack-note">매번 랜덤으로 섞이는 질문 10장</p></div></section>';
  }

  function mic() {
    return '<section class="center"><div class="mic-badge" aria-hidden="true">' +
      '<svg viewBox="0 0 24 24" width="28" height="28"><rect x="9" y="3" width="6" height="11" rx="3" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M6.5 11a5.5 5.5 0 0 0 11 0M12 16.5v4M9 20.5h6M5 5l14 14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg></div>' +
      "<h2>마이크를 켜 주세요</h2><p>말로 고르려면 마이크를 허용해 주세요.</p>" +
      form("/mic/open", '<button class="btn dark">설정 열기</button>') +
      form("/home", '<button class="link">홈으로</button>') + "</section>";
  }

  function shuffle() {
    return '<section class="center"><div class="spin" aria-hidden="true"></div>' +
      "<h2>질문 카드 섞는 중…</h2><p>오늘은 어떤 질문이 나올까요?</p></section>";
  }

  function prep() {
    var n = state.joined.length;
    return '<section class="prep"><div class="copy">' +
      '<span class="pill live">' + WAVE + "마이크 ON · " + n + "명 참여</span>" +
      "<h2>참여하는 자리를 확인해요</h2>" +
      '<p class="sub">자리를 눌러서 참여 / 미참여를 바꿀 수 있어요.</p>' +
      (state.notice ? '<p class="notice">' + esc(state.notice) + "</p>" : "") +
      form("/begin", '<button class="btn dark">시작</button>') +
      '<p class="voice">' + MIC + " “시작!” 이라고 말해도 돼요</p></div>" +
      '<aside class="seat-panel"><p class="side-cap">앞 · ' + n + "명 참여</p>" +
      form("/seat", seatCells("button", null), "seat-grid") + "</aside></section>";
  }

  function pill(freshId) {
    var n = state.joined.length;
    var id = currentId();
    var name = function (seatId) {
      return SEATS.filter(function (seat) { return seat.id === seatId; })[0].name;
    };
    if (freshId) return ["outline", name(freshId) + " 목소리 확인 중…"];
    if (state.screen === "read") return ["read", "질문 읽는 중… · " + n + "명 참여"];
    if (id) return ["live", name(id) + " 차례 · A? B? 말해 주세요"];
    return ["quiet", "모두 골랐어요"];
  }

  function play(freshId) {
    var q = question();
    var c = counts();
    var kind = pill(freshId);
    var locked = state.screen === "read" || freshId ? " disabled" : "";
    var mark = kind[0] === "live" ? WAVE : kind[0] === "read" ? '<i class="dot"></i>' : '<i class="dot ink"></i>';
    var progress = Math.round((state.index + 1) * 100 / state.deck.length);
    var tally = function (count) {
      return '<span class="tally' + (count > 0 ? " is-hot" : "") + '">' + count + "명</span>";
    };
    var options;
    if (state.screen === "answer") {
      options = form("/hear",
        '<button class="opt opt-a" name="choice" value="a"><span class="badge">A</span>' + esc(q.a) + tally(c.a) + "</button>" +
        '<span class="vs">VS</span>' +
        '<button class="opt opt-b" name="choice" value="b"><span class="badge">B</span>' + esc(q.b) + tally(c.b) + "</button>",
        "options");
    } else {
      options = '<div class="options"><div class="opt opt-a"><span class="badge">A</span>' + esc(q.a) + '<span class="tally">0명</span></div>' +
        '<span class="vs">VS</span>' +
        '<div class="opt opt-b"><span class="badge">B</span>' + esc(q.b) + '<span class="tally">0명</span></div></div>';
    }
    return '<section class="play"><div class="play-top"><div class="meta">' +
      '<p class="qmeta">Q' + (state.index + 1) + "/" + state.deck.length + " · #" + esc(q.tag) + "</p>" +
      '<div class="bar" aria-hidden="true"><i style="width: ' + progress + '%"></i></div></div>' +
      '<span class="pill is-' + kind[0] + '">' + mark + esc(kind[1]) + "</span></div>" +
      '<div class="play-body"><article class="qcard"><h2 class="qtitle">' + esc(q.title) + "</h2>" + options + "</article>" +
      '<aside class="seat-panel"><p class="side-cap">앞 · ' + state.joined.length + "명 참여</p>" +
      '<div class="seat-grid">' + seatCells("div", freshId) + "</div></aside></div>" +
      '<footer class="foot"><p class="voice">' + MIC + " “다음!” 이라고 말하면 넘어가요</p>" +
      '<div class="actions">' +
      form("/replay", '<button class="link"' + locked + ">질문 다시 듣기</button>") +
      form("/next", '<button class="btn dark"' + locked + ">다음</button>") +
      "</div></footer></section>";
  }

  function done() {
    var a = state.log.filter(function (row) { return row.a > row.b; }).length;
    var b = state.log.filter(function (row) { return row.b > row.a; }).length;
    var line = a === b ? "오늘은 비겼어요" : a > b ? "A가 " + a + "장으로 앞섰어요" : "B가 " + b + "장으로 앞섰어요";
    var rounds = state.log.map(function (row) {
      return '<article class="round"><b>#' + esc(row.tag) + "</b><span>" + esc(row.title) + "</span>" +
        '<p class="score"><em class="' + (row.a > row.b ? "win-a" : "") + '">' + esc(row.a_label) + " " + row.a + "</em> · " +
        '<em class="' + (row.b > row.a ? "win-b" : "") + '">' + esc(row.b_label) + " " + row.b + "</em></p></article>";
    }).join("");
    return '<section class="done"><div class="copy"><h2>' + state.log.length + "장을 골랐어요</h2>" +
      '<p class="sub">' + line + '</p><div class="actions">' +
      form("/again", '<button class="btn dark">다시 하기</button>') +
      form("/home", '<button class="link">홈으로</button>') +
      '</div></div><div class="rounds">' + rounds + "</div></section>";
  }

  function leaveDialog() {
    if (!state.leave) return "";
    return '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="leave-title"><div class="dialog">' +
      '<h2 id="leave-title">게임을 끝낼까요?</h2><p>지금 나가면 이 판의 기록은 사라져요.</p>' +
      '<div class="actions">' + form("/stay", '<button class="link">계속하기</button>') +
      form("/quit", '<button class="btn dark">나가기</button>') + "</div></div></div>";
  }

  var SCREENS = {
    splash: splash, home: home, mic: mic, shuffle: shuffle, seats: prep,
    read: play, answer: play, done: done
  };
  var AUTO = { splash: ["/enter", 1500], shuffle: ["/ready", 1400], read: ["/ready", 2000] };

  function later(fn, ms) { timers.push(window.setTimeout(fn, ms)); }

  function render() {
    timers.forEach(window.clearTimeout);
    timers = [];
    save();

    var freshId = fresh;
    fresh = null;
    app.setAttribute("data-screen", state.screen);
    app.innerHTML = header() + SCREENS[state.screen](freshId) + leaveDialog();

    if (freshId) later(function () { act("/settle"); }, 900);
    var auto = AUTO[state.screen];
    if (auto && !state.leave) later(function () { act(auto[0]); }, auto[1]);
  }

  app.addEventListener("submit", function (event) {
    event.preventDefault();
    var by = event.submitter;
    act(event.target.getAttribute("action"), by && by.name ? by.value : undefined);
  });

  render();
})();
