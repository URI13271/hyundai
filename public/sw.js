// 화면 서버가 잠깐 꺼져도 앱이 흰 화면이 되지 않게 한다. 판은 캐시하지 않는다.
var CACHE = "drive-talk-v1";
var SHELL = [
  "/assets/app.css",
  "/assets/adaptive.css",
  "/assets/webapp.css",
  "/assets/app.js",
  "/assets/fonts/PretendardVariable.woff2",
  "/assets/icon.svg",
  "/assets/offline.html"
];

self.addEventListener("install", function (event) {
  event.waitUntil(caches.open(CACHE).then(function (cache) { return cache.addAll(SHELL); }));
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (key) { return key !== CACHE; })
      .map(function (key) { return caches.delete(key); }));
  }).then(function () { return self.clients.claim(); }));
});

self.addEventListener("fetch", function (event) {
  var req = event.request;
  if (req.method !== "GET") return;

  var url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.indexOf("/assets/") === 0) {
    // 하루짜리 브라우저 캐시를 건너뛴다. 앱을 새로 깔지 않아도 고친 화면이 바로 뜬다.
    event.respondWith(fetch(req, { cache: "no-cache" }).then(function (res) {
      if (res.ok) {
        var copy = res.clone();
        caches.open(CACHE).then(function (cache) { cache.put(req, copy); });
      }
      return res;
    }).catch(function () { return caches.match(req); }));
  } else if (req.mode === "navigate") {
    event.respondWith(fetch(req).catch(function () { return caches.match("/assets/offline.html"); }));
  }
});
