(function () {
  var settle = document.querySelector("[data-settle]");
  if (settle) window.setTimeout(function () { post("/settle"); }, 900);

  var auto = document.querySelector("[data-auto]");
  if (auto && !document.querySelector('[role="dialog"]')) {
    window.setTimeout(function () {
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

  function post(action) {
    var form = document.createElement("form");
    form.method = "post";
    form.action = action;
    document.body.appendChild(form);
    form.submit();
  }
})();
