// 오프라인에서도 재난지도가 열리도록 앱 파일·Cesium 라이브러리·지도 타일을 캐시합니다.
// 데이터(data.js)를 바꾸면 VERSION 숫자를 올려 주세요.
const VERSION = 'v11';
const SHELL = `shell-${VERSION}`;
const LIB = 'cesium-1.123.0';
const TILES = 'tiles-v1';
const MAX_TILES = 600;
const SHELL_FILES = ['./', './index.html', './data.js', './criteria.json', './character.png', './char/walk0.png', './char/walk1.png', './char/walk2.png', './char/walk0m.png', './char/walk1m.png', './char/walk2m.png', './manifest.webmanifest', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(SHELL_FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => ![SHELL, LIB, TILES].includes(k)).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

async function trim(cacheName, max) {
  const c = await caches.open(cacheName), keys = await c.keys();
  for (let i = 0; i < keys.length - max; i++) await c.delete(keys[i]);
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // 앱 파일: 네트워크 우선(새 버전 반영), 실패하면 캐시
  if (url.origin === location.origin) {
    e.respondWith(fetch(req).then(res => {
      const copy = res.clone(); caches.open(SHELL).then(c => c.put(req, copy)); return res;
    }).catch(() => caches.match(req, { ignoreSearch: true })));
    return;
  }

  // Cesium 라이브러리(버전 고정): 캐시 우선
  if (url.hostname === 'cdn.jsdelivr.net' && (url.pathname.includes('/cesium@1.123.0/') || url.pathname.includes('/cesium@1.123/Apps/'))) {
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(LIB).then(c => c.put(req, copy)); }
      return res;
    })));
    return;
  }

  // 지도 타일: 네트워크 우선, 오프라인이면 본 적 있는 타일
  if (url.hostname.endsWith('tile.openstreetmap.org') || url.hostname === 'xdworld.vworld.kr') {
    e.respondWith(fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(TILES).then(c => c.put(req, copy)).then(() => trim(TILES, MAX_TILES)); }
      return res;
    }).catch(() => caches.match(req)));
  }
});
