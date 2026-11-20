/* 펨코 핫딜 필터 — service worker
 * 앱 껍데기는 캐시 우선, 데이터(deals.json)는 네트워크 우선 + 캐시 폴백.
 * 크롤러가 JSON을 갱신하면 앱은 새 데이터를 받고, 오프라인이면 마지막 데이터로 뜬다.
 */
const VERSION = 'v1';
const SHELL = 'fmk-shell-' + VERSION;
const DATA = 'fmk-data-' + VERSION;

const SHELL_FILES = [
  './',
  './index.html',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL).then((c) => c.addAll(SHELL_FILES)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => !k.endsWith(VERSION)).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET') return;

  // 데이터: 네트워크 우선
  if (url.pathname.endsWith('/data/deals.json')) {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(DATA).then((c) => c.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  if (url.origin !== self.location.origin) return;

  // 껍데기: 캐시 우선
  event.respondWith(
    caches.match(event.request).then((hit) => hit || fetch(event.request).catch(() => caches.match('./index.html')))
  );
});
