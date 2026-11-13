const CACHE_NAME = 'mo-stock-pwa-v3';

const STATIC_ASSETS = [
    '/css/bootstrap.min.css',
    '/css/bootstrap-icons.min.css',
    '/css/fonts/bootstrap-icons.woff2',
    '/css/fonts/bootstrap-icons.woff',
    '/css/fonts/bootstrap-icons.woff2?dd67030699838ea613ee6dbda90effa6',
    '/css/fonts/bootstrap-icons.woff?dd67030699838ea613ee6dbda90effa6',
    '/css/select2.min.css',
    '/css/select2-bootstrap-5-theme.min.css',
    '/css/styles.css',
    '/css/print.css',
    '/js/bootstrap.bundle.min.js',
    '/js/jquery.min.js',
    '/js/select2.min.js',
    '/js/html5-qrcode.min.js',
    '/js/scripts.js',
    '/js/offline-sync.js',
    '/favicon.svg',
    '/manifest.json'
];

const APP_PAGES = [
    '/dashboard',
    '/batches',
    '/batches/create',
    '/batches/manual-labels',
    '/batches/offline-print',
    '/procurement',
    '/admin/settings'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(async (cache) => {
            await Promise.allSettled(
                STATIC_ASSETS.map((assetUrl) => cache.add(assetUrl))
            );
            await Promise.allSettled(
                APP_PAGES.map((url) =>
                    fetch(url, { credentials: 'same-origin' }).then((resp) => {
                        if (resp.ok && !resp.redirected) {
                            return cache.put(url, resp);
                        }
                    })
                )
            );
        })
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(
                keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
            )
        )
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') {
        return;
    }

    const url = new URL(req.url);

    // Skip API requests (offline-sync.js handles API snapshot & outbox in IndexedDB)
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/h2-console')) {
        return;
    }

    // Static assets (CSS, JS, Bootstrap Icon fonts, SVGs, Manifest): Cache-First with ignoreSearch fallback
    if (
        url.pathname.startsWith('/css/') ||
        url.pathname.startsWith('/js/') ||
        url.pathname.endsWith('.woff2') ||
        url.pathname.endsWith('.woff') ||
        url.pathname.endsWith('.ttf') ||
        url.pathname.endsWith('.svg') ||
        url.pathname === '/manifest.json'
    ) {
        event.respondWith(
            caches.match(req).then(async (exactCached) => {
                if (exactCached) {
                    return exactCached;
                }
                const ignoreSearchCached = await caches.match(url.pathname, { ignoreSearch: true });
                if (ignoreSearchCached) {
                    return ignoreSearchCached;
                }
                return fetch(req).then((networkResp) => {
                    if (networkResp && networkResp.status === 200) {
                        const clone = networkResp.clone();
                        caches.open(CACHE_NAME).then((c) => {
                            c.put(req, clone.clone());
                            c.put(url.pathname, clone);
                        });
                    }
                    return networkResp;
                });
            })
        );
        return;
    }

    // HTML navigation requests: Network-First with Cache Fallback
    if (req.mode === 'navigate' || (req.headers.get('accept') || '').includes('text/html')) {
        event.respondWith(
            fetch(req)
                .then((networkResp) => {
                    if (networkResp && networkResp.status === 200 && !networkResp.redirected) {
                        const clone = networkResp.clone();
                        caches.open(CACHE_NAME).then((cache) => {
                            cache.put(req, clone.clone());
                            cache.put(url.pathname, clone);
                        });
                    }
                    return networkResp;
                })
                .catch(async () => {
                    const cache = await caches.open(CACHE_NAME);
                    const exactMatch = await cache.match(req);
                    if (exactMatch) {
                        return exactMatch;
                    }
                    const pathMatch = await cache.match(url.pathname);
                    if (pathMatch) {
                        return pathMatch;
                    }
                    if (/^\/batches\/\d+$/.test(url.pathname)) {
                        const batchesListMatch = await cache.match('/batches');
                        if (batchesListMatch) {
                            return batchesListMatch;
                        }
                    }
                    const dashboardFallback = await cache.match('/dashboard');
                    if (dashboardFallback) {
                        return dashboardFallback;
                    }
                    return new Response(
                        '<!DOCTYPE html><html><body style="font-family:sans-serif;padding:2rem;"><h3>Offline Mode</h3><p>Return to <a href="/dashboard">Home</a>, <a href="/batches">Batches</a>, <a href="/batches/manual-labels">Manual Labels</a>, or <a href="/procurement">Procurement Scheduler</a>.</p></body></html>',
                        { headers: { 'Content-Type': 'text/html' } }
                    );
                })
        );
    }
});
