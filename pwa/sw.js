/* Rubato Guru PWA — app-shell cache, API sempre rete. */
const CACHE = 'rubato-guru-v2';
const SHELL = ['/app/', '/app/index.html', '/app/style.css', '/app/app.js',
  '/app/manifest.webmanifest', '/app/icons/icon-192.png', '/app/icons/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (url.pathname.startsWith('/app/') && !url.pathname.startsWith('/app/icons/')) {
    // shell: cache-first con fallback rete (ma mai le API: quelle stanno fuori /app/)
    e.respondWith(
      caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })));
    return;
  }
  e.respondWith(fetch(e.request));
});
