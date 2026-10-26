const CACHE = 'custerra-offline-shell-v3';
const ASSETS = ['/offline.html', '/offline-viewer.js', '/offline-store.js', '/offline.css'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('custerra-offline-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (ASSETS.includes(url.pathname) && !url.search) {
    event.respondWith(caches.open(CACHE).then(async cache => (await cache.match(event.request)) || fetch(event.request)));
  } else if (event.request.mode === 'navigate' && !url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(event.request).catch(async () => (await caches.open(CACHE)).match('/offline.html').then(response => response || Response.error())));
  }
});

// Remember displayed message identities separately from offline journey copies.
function claimPush(id) {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open('custerra-push-dedup', 1);
    open.onupgradeneeded = () => open.result.createObjectStore('seen');
    open.onerror = () => reject(new Error('Push deduplication storage unavailable'));
    open.onsuccess = () => {
      const db = open.result; const tx = db.transaction('seen', 'readwrite'); const store = tx.objectStore('seen'); let fresh = false;
      const get = store.get(id);
      get.onsuccess = () => { if (!get.result) { fresh = true; store.put(Date.now(), id); } };
      const cursor = store.openCursor(); let count = 0;
      cursor.onsuccess = () => { const row = cursor.result; if (row) { if (Date.now() - row.value > 7 * 86400000 || ++count > 1000) row.delete(); row.continue(); } };
      tx.oncomplete = () => { db.close(); resolve(fresh); }; tx.onerror = () => { db.close(); reject(tx.error); };
    };
  });
}
self.addEventListener('push', event => {
  event.waitUntil((async () => {
    let payload; try { payload = event.data?.json(); } catch { return; }
    if (!payload || typeof payload.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(payload.id) || typeof payload.title !== 'string' || typeof payload.body !== 'string') return;
    // Stable browser tag additionally replaces an already-visible copy if storage was evicted.
    if (!await claimPush(payload.id).catch(() => true)) return;
    await self.registration.showNotification(payload.title.slice(0,160), { body: payload.body.slice(0,800), tag: `custerra-${payload.id}`, renotify: false, data: { id: payload.id } });
  })());
});
self.addEventListener('notificationclick', event => {
  event.notification.close(); const id = event.notification.data?.id;
  if (typeof id !== 'string' || !/^[0-9a-f-]{36}$/i.test(id)) return;
  const url = new URL(`/alerts?notice=${id}`, self.location.origin).href;
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async windows => {
    const existing = windows.find(window => new URL(window.url).origin === self.location.origin);
    if (existing) { await existing.navigate(url); return existing.focus(); } return self.clients.openWindow(url);
  }));
});
