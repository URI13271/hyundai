(function () {
  // 안드로이드 앱(android/)이 이 화면을 띄우면 틀 없이 화면을 다 쓴다.
  if (/DriveTalkApp/.test(navigator.userAgent)) document.documentElement.classList.add("app");

  var timers = [];
  var busy = false;

  function later(fn, ms) { timers.push(window.setTimeout(fn, ms)); }

  // 화면이 바뀔 때마다 다시 부른다.
  function init() {
    var settle = document.querySelector("[data-settle]");
    if (settle) later(function () { post("/settle"); }, 900);

    var auto = document.querySelector("[data-auto]");
    if (auto && !document.querySelector('[role="dialog"]')) {
      later(function () {
        post(auto.getAttribute("data-auto"));
      }, Number(auto.getAttribute("data-delay")) || 1500);
    }

    var start = document.querySelector("[data-need-mic]");
    var demo = document.body.getAttribute("data-demo") === "1";
    if (start && !demo && navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      start.addEventListener("click", function (event) {
        event.preventDefault();
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
          stream.getTracks().forEach(function (track) { track.stop(); });
          post("/start");
        }).catch(function () {
          post("/mic");
        });
      });
    }
  }

  // 페이지를 새로 열지 않고 다음 화면만 받아 갈아 끼운다.
  function post(action, body) {
    if (busy) return;
    busy = true;
    fetch(action, { method: "POST", body: body || new URLSearchParams(), credentials: "same-origin" })
      .then(function (res) {
        return res.text().then(function (html) { show(html, res.url); });
      })
      .catch(function () { window.location.reload(); });
  }

  function show(html, url) {
    var doc = new DOMParser().parseFromString(html, "text/html");
    var next = doc.querySelector(".stage");
    var stage = document.querySelector(".stage");
    if (!next || !stage) {
      window.location.href = url;
      return;
    }

    timers.forEach(window.clearTimeout);
    timers = [];
    document.body.className = doc.body.className;
    if (doc.body.hasAttribute("data-demo")) {
      document.body.setAttribute("data-demo", doc.body.getAttribute("data-demo"));
    }
    stage.replaceWith(document.importNode(next, true));
    window.history.replaceState(null, "", url);
    busy = false;
    init();
  }

  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (String(form.method).toLowerCase() !== "post") return;

    event.preventDefault();
    var data = new URLSearchParams(new FormData(form));
    var by = event.submitter;
    if (by && by.name) data.append(by.name, by.value);
    post(form.getAttribute("action"), data);
  });

  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js").catch(function () {});
  }

  init();
})();
