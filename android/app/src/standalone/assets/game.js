// 서버 없이 도는 드라이브 토크의 화면. views/screen.erb를 그대로 옮겼다.
// 판(덱, 좌석, 선택, 집계, 기록)은 앱 안의 코틀린 백엔드가 갖는다. android/backend.
// 여기에는 스플래시, 마이크 안내, 나가기 확인, 목소리 확인 표시만 있다. lib/drive_talk/remote_game.rb와 같은 자리다.
(function () {
  "use strict";

  var KEY = "drive-talk";
  var SEATS = [
    { id: "driver", name: "운전석", driver: true },
    { id: "passenger", name: "조수석", driver: false },
    { id: "rear_left", name: "뒷좌석 왼쪽", driver: false },
    { id: "rear_right", name: "뒷좌석 오른쪽", driver: false }
  ];
  // 홈의 01 여행 카드는 시안 그대로. 실제 10장은 백엔드가 섞어 준다.
  var SAMPLE = { tag: "여행", title: "여행을 간다면 어디로?", a: "산", b: "바다" };
  var MATCH = ["shuffle", "seats", "read", "answer"];

  var app = document.getElementById("app");
  var timers = [];
  var fresh = null;
  var saved = load();
  var ui = { screen: "splash", mic: saved.mic, leave: false, notice: null };
  var sid = saved.sid;
  var view = null;

  // 백엔드

  // 돌아오는 것은 { status, body }. status는 HTTP 상태 코드와 같은 뜻이다.
  function call(method, path, body) {
    try {
      return JSON.parse(window.DriveTalkBackend.call(method, path, body ? JSON.stringify(body) : ""));
    } catch (e) {
      return { status: 500, body: { error: String(e) } };
    }
  }

  function load() {
    try {
      var found = JSON.parse(window.localStorage.getItem(KEY)) || {};
      return { sid: found.sid || null, mic: found.mic === undefined ? null : found.mic };
    } catch (e) {
      return { sid: null, mic: null };
    }
  }

  function save() {
    try {
      window.localStorage.setItem(KEY, JSON.stringify({ sid: sid, mic: ui.mic }));
    } catch (e) { /* 저장 못 해도 판은 백엔드에 있다 */ }
  }

  // 앱을 다시 켜면 하던 판으로 돌아온다. 판이 없어졌으면 처음부터다.
  function sync() {
    if (!sid) return;
    var res = call("GET", "/v1/sessions/" + sid);
    if (res.status === 200) view = res.body;
    else forget();
  }

  // 거절은 화면을 바꾸지 않는다. 판이 없어졌으면 홈으로 간다.
  function step(name, body) {
    if (!sid) return 404;
    var res = call("POST", "/v1/sessions/" + sid + "/" + name, body);
    if (res.status === 200) view = res.body;
    else if (res.status === 404) forget();
    return res.status;
  }

  function forget() {
    sid = null;
    view = null;
    resetMatch();
    ui.screen = "home";
  }

  function drop() {
    if (sid) call("DELETE", "/v1/sessions/" + sid);
    sid = null;
    view = null;
  }

  // 판

  function screen() { return view ? view.screen : ui.screen; }
  function inMatch() { return MATCH.indexOf(screen()) >= 0; }
  function question() { return view.deck[view.index]; }
  function joinedCount() { return view.seats.filter(function (seat) { return seat.joined; }).length; }

  function currentId() {
    for (var i = 0; i < view.seats.length; i++) {
      if (view.seats[i].joined && !view.seats[i].choice) return view.seats[i].id;
    }
    return null;
  }

  function seats() {
    var turn = currentId();
    return SEATS.map(function (seat, i) {
      var found = view.seats[i];
      return {
        id: seat.id, name: seat.name, driver: seat.driver, joined: found.joined,
        choice: found.choice, turn: !found.choice && seat.id === turn
      };
    });
  }

  // 차량 착석 센서(Pleos)가 아는 자리는 앉은 사람대로 참여를 맞춘다.
  // 센서가 모르는 자리는 손대지 않는다. 자리 화면에서만 맞추고, 그 뒤에는 손으로 바꿀 수 있다.
  function occupancy() {
    try {
      return window.DriveTalkVehicle ? JSON.parse(window.DriveTalkVehicle.occupancy()) : {};
    } catch (e) {
      return {};
    }
  }

  function applyOccupancy() {
    if (screen() !== "seats") return;
    var known = occupancy();
    view.seats.forEach(function (seat) {
      var sat = known[seat.id];
      if (typeof sat === "boolean" && sat !== seat.joined) step("seats", { id: seat.id });
    });
  }

  window.onSeatOccupancy = function () {
    if (screen() !== "seats") return;
    applyOccupancy();
    render();
  };

  function resetMatch() {
    drop();
    ui.leave = false;
    ui.notice = null;
    fresh = null;
  }

  function startShuffle() {
    resetMatch();
    var res = call("POST", "/v1/sessions");
    if (res.status === 200) {
      view = res.body;
      sid = view.id;
    } else {
      ui.screen = "home";
    }
  }

  function showMic() {
    if (inMatch()) return;
    ui.leave = false;
    ui.screen = "mic";
  }

  var ACTIONS = {
    "/enter": function () { if (screen() === "splash") ui.screen = "home"; },
    "/start": function () { if (ui.mic === false) showMic(); else startShuffle(); },
    "/mic": showMic,
    "/mic/open": function () { ui.mic = true; startShuffle(); },
    "/home": function () { resetMatch(); ui.screen = "home"; },
    "/ready": function () {
      if (screen() === "shuffle" || screen() === "read") step("ready");
      applyOccupancy();
    },
    "/seat": function (id) {
      if (screen() !== "seats") return;
      ui.notice = null;
      step("seats", { id: id });
    },
    "/begin": function () {
      if (screen() !== "seats") return;
      ui.notice = null;
      fresh = null;
      if (step("begin") === 422) ui.notice = "한 자리는 참여해야 시작할 수 있어요.";
    },
    "/hear": function (choice) {
      if (screen() !== "answer" || (choice !== "a" && choice !== "b")) return;
      var id = currentId();
      if (id && step("hear", { choice: choice }) === 200) fresh = id;
    },
    "/settle": function () {},
    "/replay": function () {
      if (screen() !== "answer") return;
      fresh = null;
      step("replay");
    },
    "/next": function () {
      if (screen() !== "answer") return;
      fresh = null;
      step("next");
    },
    "/leave": function () { if (inMatch()) ui.leave = true; },
    "/stay": function () { ui.leave = false; },
    "/quit": function () { resetMatch(); ui.screen = "home"; },
    "/again": startShuffle
  };

  function act(path, value) {
    var run = ACTIONS[path];
    if (!run) return;
    run(value);
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
    if (screen() === "splash") return "";
    var back = "";
    if (screen() === "mic" || inMatch()) {
      back = form(screen() === "mic" ? "/home" : "/leave",
        '<button class="back" aria-label="뒤로"><svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M14.5 5.5 L8 12 L14.5 18.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></button>');
    }
    return '<header class="top">' + back + '<p class="brand">드라이브 토크</p></header>';
  }

  function seatClass(seat, freshId) {
    if (!seat.joined) return "is-out";
    if (seat.id === freshId) return "is-fresh";
    if (seat.choice === "a") return "is-a";
    if (seat.choice === "b") return "is-b";
    if (seat.turn && screen() === "answer") return "is-turn";
    if (screen() === "read") return "is-wait";
    return "is-in";
  }

  function seatState(seat, freshId) {
    if (!seat.joined) return "미참여";
    if (seat.id === freshId) return "말하는 중";
    if (seat.choice === "a") return "A 선택";
    if (seat.choice === "b") return "B 선택";
    if (seat.turn && screen() === "answer") return "차례예요";
    if (screen() === "read") return "대기 중";
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
    var q = SAMPLE;
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
    var n = joinedCount();
    return '<section class="prep"><div class="copy">' +
      '<span class="pill live">' + WAVE + "마이크 ON · " + n + "명 참여</span>" +
      "<h2>참여하는 자리를 확인해요</h2>" +
      '<p class="sub">자리를 눌러서 참여 / 미참여를 바꿀 수 있어요.</p>' +
      (ui.notice ? '<p class="notice">' + esc(ui.notice) + "</p>" : "") +
      form("/begin", '<button class="btn dark">시작</button>') +
      '<p class="voice">' + MIC + " “시작!” 이라고 말해도 돼요</p></div>" +
      '<aside class="seat-panel"><p class="side-cap">앞 · ' + n + "명 참여</p>" +
      form("/seat", seatCells("button", null), "seat-grid") + "</aside></section>";
  }

  function pill(freshId) {
    var id = currentId();
    var name = function (seatId) {
      return SEATS.filter(function (seat) { return seat.id === seatId; })[0].name;
    };
    if (freshId) return ["outline", name(freshId) + " 목소리 확인 중…"];
    if (screen() === "read") return ["read", "질문 읽는 중… · " + joinedCount() + "명 참여"];
    if (id) return ["live", name(id) + " 차례 · A? B? 말해 주세요"];
    return ["quiet", "모두 골랐어요"];
  }

  function play(freshId) {
    var q = question();
    var c = view.counts;
    var kind = pill(freshId);
    var locked = screen() === "read" || freshId ? " disabled" : "";
    var mark = kind[0] === "live" ? WAVE : kind[0] === "read" ? '<i class="dot"></i>' : '<i class="dot ink"></i>';
    var progress = Math.round((view.index + 1) * 100 / view.deck.length);
    var tally = function (count) {
      return '<span class="tally' + (count > 0 ? " is-hot" : "") + '">' + count + "명</span>";
    };
    var options;
    if (screen() === "answer") {
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
      '<p class="qmeta">Q' + (view.index + 1) + "/" + view.deck.length + " · #" + esc(q.tag) + "</p>" +
      '<div class="bar" aria-hidden="true"><i style="width: ' + progress + '%"></i></div></div>' +
      '<span class="pill is-' + kind[0] + '">' + mark + esc(kind[1]) + "</span></div>" +
      '<div class="play-body"><article class="qcard"><h2 class="qtitle">' + esc(q.title) + "</h2>" + options + "</article>" +
      '<aside class="seat-panel"><p class="side-cap">앞 · ' + joinedCount() + "명 참여</p>" +
      '<div class="seat-grid">' + seatCells("div", freshId) + "</div></aside></div>" +
      '<footer class="foot"><p class="voice">' + MIC + " “다음!” 이라고 말하면 넘어가요</p>" +
      '<div class="actions">' +
      form("/replay", '<button class="link"' + locked + ">질문 다시 듣기</button>") +
      form("/next", '<button class="btn dark"' + locked + ">다음</button>") +
      "</div></footer></section>";
  }

  function done() {
    var log = view.log;
    var a = log.filter(function (row) { return row.a > row.b; }).length;
    var b = log.filter(function (row) { return row.b > row.a; }).length;
    var line = a === b ? "오늘은 비겼어요" : a > b ? "A가 " + a + "장으로 앞섰어요" : "B가 " + b + "장으로 앞섰어요";
    var rounds = log.map(function (row) {
      return '<article class="round"><b>#' + esc(row.tag) + "</b><span>" + esc(row.title) + "</span>" +
        '<p class="score"><em class="' + (row.a > row.b ? "win-a" : "") + '">' + esc(row.a_label) + " " + row.a + "</em> · " +
        '<em class="' + (row.b > row.a ? "win-b" : "") + '">' + esc(row.b_label) + " " + row.b + "</em></p></article>";
    }).join("");
    return '<section class="done"><div class="copy"><h2>' + log.length + "장을 골랐어요</h2>" +
      '<p class="sub">' + line + '</p><div class="actions">' +
      form("/again", '<button class="btn dark">다시 하기</button>') +
      form("/home", '<button class="link">홈으로</button>') +
      '</div></div><div class="rounds">' + rounds + "</div></section>";
  }

  function leaveDialog() {
    if (!ui.leave) return "";
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
    var now = screen();
    app.setAttribute("data-screen", now);
    app.innerHTML = header() + SCREENS[now](freshId) + leaveDialog();

    if (freshId) later(function () { act("/settle"); }, 900);
    var auto = AUTO[now];
    if (auto && !ui.leave) later(function () { act(auto[0]); }, auto[1]);
  }

  app.addEventListener("submit", function (event) {
    event.preventDefault();
    var by = event.submitter;
    act(event.target.getAttribute("action"), by && by.name ? by.value : undefined);
  });

  sync();
  render();
})();
