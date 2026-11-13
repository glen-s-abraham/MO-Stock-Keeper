const CACHE_NAME = 'mo-stock-pwa-v4';

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
    '/',
    '/dashboard',
    '/batches',
    '/batches/create',
    '/batches/manual-labels',
    '/batches/offline-print',
    '/procurement',
    '/admin/settings'
];

async function cacheStaticAssets(cache) {
    await Promise.allSettled(
        STATIC_ASSETS.map(async (assetUrl) => {
            try {
                const resp = await fetch(assetUrl, { credentials: 'same-origin', cache: 'no-cache' });
                if (resp && resp.status === 200) {
                    const cleanPath = assetUrl.split('?')[0];
                    await cache.put(assetUrl, resp.clone());
                    if (cleanPath !== assetUrl) {
                        await cache.put(cleanPath, resp);
                    }
                }
            } catch (ignored) {}
        })
    );
}

async function cacheAppPages(cache) {
    await Promise.allSettled(
        APP_PAGES.map(async (url) => {
            try {
                const resp = await fetch(url, { credentials: 'same-origin', cache: 'no-cache' });
                if (resp && resp.status === 200 && !resp.redirected && !(resp.url || '').includes('/login')) {
                    await cache.put(url, resp.clone());
                    if (url === '/dashboard') {
                        await cache.put('/', resp);
                    }
                }
            } catch (ignored) {}
        })
    );
}

self.addEventListener('install', (event) => {
    self.skipWaiting();
    event.waitUntil(
        caches.open(CACHE_NAME).then(async (cache) => {
            await cacheStaticAssets(cache);
            await cacheAppPages(cache);
        })
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        (async () => {
            const keys = await caches.keys();
            await Promise.all(
                keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
            );
            await self.clients.claim();
        })()
    );
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'WARM_CACHE') {
        event.waitUntil(
            caches.open(CACHE_NAME).then(async (cache) => {
                await cacheStaticAssets(cache);
                await cacheAppPages(cache);
            })
        );
    }
});

self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') {
        return;
    }

    const url = new URL(req.url);
    if (url.origin !== self.location.origin) {
        return;
    }

    // Skip API requests (offline-sync.js handles API snapshot & outbox in IndexedDB/localStorage)
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/h2-console')) {
        return;
    }

    // Static assets (CSS, JS, Bootstrap Icon fonts, SVGs, Manifest): Cache-First with ignoreVary + ignoreSearch
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
            (async () => {
                const cache = await caches.open(CACHE_NAME);
                const exactCached = await cache.match(req, { ignoreVary: true });
                if (exactCached) {
                    return exactCached;
                }
                const pathCached = await cache.match(url.pathname, { ignoreSearch: true, ignoreVary: true });
                if (pathCached) {
                    return pathCached;
                }
                try {
                    const networkResp = await fetch(req);
                    if (networkResp && networkResp.status === 200) {
                        const clone1 = networkResp.clone();
                        const clone2 = networkResp.clone();
                        cache.put(req, clone1).catch(() => {});
                        cache.put(url.pathname, clone2).catch(() => {});
                    }
                    return networkResp;
                } catch (err) {
                    const anyMatch = await caches.match(url.pathname, { ignoreSearch: true, ignoreVary: true });
                    if (anyMatch) return anyMatch;
                    throw err;
                }
            })()
        );
        return;
    }

    // HTML navigation requests: Network-First with robust Cache Fallback (ignoreVary + ignoreSearch)
    if (req.mode === 'navigate' || (req.headers.get('accept') || '').includes('text/html')) {
        event.respondWith(
            (async () => {
                const cache = await caches.open(CACHE_NAME);
                try {
                    const networkResp = await fetch(req);
                    if (
                        networkResp &&
                        networkResp.status === 200 &&
                        !networkResp.redirected &&
                        !(networkResp.url || '').includes('/login')
                    ) {
                        const clone1 = networkResp.clone();
                        const clone2 = networkResp.clone();
                        cache.put(req, clone1).catch(() => {});
                        cache.put(url.pathname, clone2).catch(() => {});
                        if (url.pathname === '/dashboard' || url.pathname === '/') {
                            const clone3 = networkResp.clone();
                            cache.put('/dashboard', clone3).catch(() => {});
                            cache.put('/', clone3.clone()).catch(() => {});
                        }
                    }
                    return networkResp;
                } catch (err) {
                    // 1. Exact request match ignoring Vary
                    const exactMatch = await cache.match(req, { ignoreVary: true });
                    if (exactMatch) {
                        return exactMatch;
                    }
                    // 2. Pathname match ignoring query string and Vary
                    const pathMatch = await cache.match(url.pathname, { ignoreSearch: true, ignoreVary: true });
                    if (pathMatch) {
                        return pathMatch;
                    }
                    // 3. Root / dashboard interchangeability
                    if (url.pathname === '/' || url.pathname === '/dashboard') {
                        const dashMatch =
                            (await cache.match('/dashboard', { ignoreSearch: true, ignoreVary: true })) ||
                            (await cache.match('/', { ignoreSearch: true, ignoreVary: true }));
                        if (dashMatch) {
                            return dashMatch;
                        }
                    }
                    // 4. Individual batch view -> fallback to /batches list (where offline modal opens)
                    if (/^\/batches\/\d+$/.test(url.pathname)) {
                        const batchesListMatch = await cache.match('/batches', { ignoreSearch: true, ignoreVary: true });
                        if (batchesListMatch) {
                            return batchesListMatch;
                        }
                    }
                    // 5. Fallback to cached dashboard or batches page
                    const fallbackPage =
                        (await cache.match('/dashboard', { ignoreSearch: true, ignoreVary: true })) ||
                        (await cache.match('/batches', { ignoreSearch: true, ignoreVary: true }));
                    if (fallbackPage) {
                        return fallbackPage;
                    }
                    return new Response(
                        '<!DOCTYPE html><html><body style="font-family:sans-serif;padding:2rem;"><h3>Offline Mode</h3><p>Return to <a href="/dashboard">Home</a>, <a href="/batches">Batches</a>, <a href="/batches/manual-labels">Manual Labels</a>, or <a href="/procurement">Procurement Scheduler</a>.</p></body></html>',
                        { headers: { 'Content-Type': 'text/html' } }
                    );
                }
            })()
        );
    }
});
