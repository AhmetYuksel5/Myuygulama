/*
 * Çevrimdışı çalışması için.
 *
 * Uygulamanın kendi dosyaları kuruluşta saklanıyor; kitaplar ve deste
 * zaten cihazda olduğu için internetsiz okumak mümkün. OpenAI istekleri
 * doğal olarak internet istiyor, onlar buradan geçmiyor.
 */
const SURUM = "merkez-2";
const DOSYALAR = [
  ".", "index.html", "app.css", "manifest.webmanifest",
  "js/app.js", "js/depo.js", "js/epub.js", "js/tekrar.js", "js/yapayzeka.js",
];

self.addEventListener("install", olay => {
  olay.waitUntil(caches.open(SURUM).then(k => k.addAll(DOSYALAR)));
  self.skipWaiting();
});

self.addEventListener("activate", olay => {
  olay.waitUntil(
    caches.keys().then(adlar =>
      Promise.all(adlar.filter(a => a !== SURUM).map(a => caches.delete(a)))),
  );
  self.clients.claim();
});

self.addEventListener("fetch", olay => {
  const istek = olay.request;
  if (istek.method !== "GET" || new URL(istek.url).origin !== location.origin) return;
  // Önce ağ, olmazsa saklanan: yeni sürüm çıktığında eski dosyaya
  // takılıp kalmasın, ama uçak modunda da açılsın.
  olay.respondWith(
    fetch(istek).then(yanit => {
      const kopya = yanit.clone();
      caches.open(SURUM).then(k => k.put(istek, kopya));
      return yanit;
    }).catch(() => caches.match(istek).then(y => y || caches.match("index.html"))),
  );
});
