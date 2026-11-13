/**
 * MO-Stock-Keeper Offline-First PWA & Outbox Sync Engine (009-offline-pwa-sync)
 * Supports offline Harvest Batches (Create, View, Edit, Delete, Spoil Unit, Print All/Range/Single),
 * Manual Label Printing, System Settings, and the Adaptive Rolling Procurement Scheduler (ARPS).
 */
(function () {
    'use strict';

    const DB_NAME = 'mo_stock_keeper_offline_v1';
    const DB_VERSION = 2;
    const SW_CACHE_NAME = 'mo-stock-pwa-v4';
    const SNAPSHOT_KEY = 'mo_stock_offline_snapshot_v1';
    const OUTBOX_KEY = 'mo_stock_offline_outbox_v1';
    const PRINT_JOB_KEY = 'mo_stock_latest_offline_print_job';
    const PAGE_CACHE_PREFIX = 'mo_stock_page_html_';
    const OFFLINE_APP_PAGES = [
        '/dashboard',
        '/batches',
        '/batches/create',
        '/batches/manual-labels',
        '/batches/offline-print',
        '/procurement',
        '/admin/settings'
    ];

    let dbInstance = null;
    let isSyncing = false;
    let isWarmingPages = false;
    let activeModalBatchCode = null;

    // 1. Register Service Worker immediately (do not wait for window.load)
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker
            .register('/sw.js', { scope: '/' })
            .then(function (reg) {
                const targetSw = reg.active || reg.waiting || reg.installing;
                if (targetSw && navigator.onLine) {
                    targetSw.postMessage({ type: 'WARM_CACHE' });
                }
            })
            .catch(function () {});
    }

    // 2. IndexedDB + localStorage Dual-Layer Storage (Snapshots, Outbox & HTML Pages)
    function openDatabase() {
        return new Promise((resolve) => {
            if (dbInstance) {
                resolve(dbInstance);
                return;
            }
            if (!window.indexedDB) {
                resolve(null);
                return;
            }
            try {
                const req = window.indexedDB.open(DB_NAME, DB_VERSION);
                req.onupgradeneeded = function (e) {
                    const db = e.target.result;
                    if (!db.objectStoreNames.contains('snapshot')) {
                        db.createObjectStore('snapshot');
                    }
                    if (!db.objectStoreNames.contains('outbox')) {
                        db.createObjectStore('outbox', { keyPath: 'clientMutationId' });
                    }
                    if (!db.objectStoreNames.contains('pages')) {
                        db.createObjectStore('pages');
                    }
                };
                req.onsuccess = function (e) {
                    dbInstance = e.target.result;
                    resolve(dbInstance);
                };
                req.onerror = function () {
                    resolve(null);
                };
            } catch (err) {
                resolve(null);
            }
        });
    }

    function normalizeOfflinePath(rawPath) {
        if (!rawPath) return '/dashboard';
        const clean = rawPath.split('?')[0].split('#')[0] || '/';
        if (clean === '/') return '/dashboard';
        if (/^\/batches\/\d+$/.test(clean)) return '/batches';
        return clean;
    }

    async function savePageHtmlLocal(path, html) {
        if (!path || !html || html.includes('action="/login"') || html.includes('name="username"')) {
            return;
        }
        const norm = normalizeOfflinePath(path);
        try {
            sessionStorage.setItem(PAGE_CACHE_PREFIX + norm, html);
        } catch (ignored) {}
        try {
            localStorage.setItem(PAGE_CACHE_PREFIX + norm, html);
        } catch (ignored) {}
        const db = await openDatabase();
        if (db && db.objectStoreNames.contains('pages')) {
            try {
                const tx = db.transaction('pages', 'readwrite');
                tx.objectStore('pages').put(html, norm);
            } catch (ignored) {}
        }
    }

    async function getPageHtmlLocal(path) {
        const norm = normalizeOfflinePath(path);
        try {
            const fromSession = sessionStorage.getItem(PAGE_CACHE_PREFIX + norm);
            if (fromSession) return fromSession;
        } catch (ignored) {}
        try {
            const fromLocal = localStorage.getItem(PAGE_CACHE_PREFIX + norm);
            if (fromLocal) return fromLocal;
        } catch (ignored) {}
        const db = await openDatabase();
        if (db && db.objectStoreNames.contains('pages')) {
            const fromIdb = await new Promise((resolve) => {
                try {
                    const tx = db.transaction('pages', 'readonly');
                    const req = tx.objectStore('pages').get(norm);
                    req.onsuccess = () => resolve(req.result || null);
                    req.onerror = () => resolve(null);
                } catch (err) {
                    resolve(null);
                }
            });
            if (fromIdb) return fromIdb;
        }
        if (window.caches) {
            try {
                const cache = await window.caches.open(SW_CACHE_NAME);
                const resp = await cache.match(norm, { ignoreSearch: true, ignoreVary: true });
                if (resp && resp.ok) {
                    return await resp.text();
                }
            } catch (ignored) {}
        }
        return null;
    }

    async function warmAllOfflineCaches() {
        if (!navigator.onLine || isWarmingPages || window.location.pathname.startsWith('/login')) {
            return;
        }
        isWarmingPages = true;
        try {
            if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                navigator.serviceWorker.controller.postMessage({ type: 'WARM_CACHE' });
            }

            const swCache = window.caches ? await window.caches.open(SW_CACHE_NAME).catch(() => null) : null;

            await Promise.allSettled(
                OFFLINE_APP_PAGES.map(async (pageUrl) => {
                    try {
                        const resp = await fetch(pageUrl, { credentials: 'same-origin', cache: 'no-cache' });
                        if (resp && resp.status === 200 && !resp.redirected && !(resp.url || '').includes('/login')) {
                            if (swCache) {
                                await swCache.put(pageUrl, resp.clone()).catch(() => {});
                                if (pageUrl === '/dashboard') {
                                    await swCache.put('/', resp.clone()).catch(() => {});
                                }
                            }
                            const html = await resp.text();
                            await savePageHtmlLocal(pageUrl, html);
                        }
                    } catch (ignored) {}
                })
            );
        } finally {
            isWarmingPages = false;
        }
    }

    async function saveSnapshotLocal(snapshot) {
        if (!snapshot) return;
        try {
            localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot));
        } catch (ignored) {}
        const db = await openDatabase();
        if (db) {
            try {
                const tx = db.transaction('snapshot', 'readwrite');
                tx.objectStore('snapshot').put(snapshot, 'current');
            } catch (ignored) {}
        }
    }

    async function getSnapshotLocal() {
        try {
            const raw = localStorage.getItem(SNAPSHOT_KEY);
            if (raw) {
                return JSON.parse(raw);
            }
        } catch (ignored) {}
        const db = await openDatabase();
        if (!db) return null;
        return new Promise((resolve) => {
            try {
                const tx = db.transaction('snapshot', 'readonly');
                const req = tx.objectStore('snapshot').get('current');
                req.onsuccess = () => resolve(req.result || null);
                req.onerror = () => resolve(null);
            } catch (err) {
                resolve(null);
            }
        });
    }

    function getOutboxLocal() {
        try {
            const raw = localStorage.getItem(OUTBOX_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch (err) {
            return [];
        }
    }

    async function saveOutboxLocal(outbox) {
        try {
            localStorage.setItem(OUTBOX_KEY, JSON.stringify(outbox));
        } catch (ignored) {}
        const db = await openDatabase();
        if (db) {
            try {
                const tx = db.transaction('outbox', 'readwrite');
                const store = tx.objectStore('outbox');
                store.clear();
                outbox.forEach((m) => store.put(m));
            } catch (ignored) {}
        }
        updateNavbarStatus();
    }

    async function enqueueMutation(mutationType, payload) {
        const outbox = getOutboxLocal();
        const mutation = {
            clientMutationId: 'mut-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8),
            mutationType: mutationType,
            createdAt: new Date().toISOString(),
            payload: payload
        };
        outbox.push(mutation);
        await saveOutboxLocal(outbox);
        return mutation;
    }

    // 3. Date & ARPS Helpers
    function parseIsoDate(str) {
        const parts = str.split('-').map(Number);
        return new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
    }

    function formatIsoDate(date) {
        return date.toISOString().split('T')[0];
    }

    function addDays(isoDateStr, days) {
        const d = parseIsoDate(isoDateStr);
        d.setUTCDate(d.getUTCDate() + Number(days));
        return formatIsoDate(d);
    }

    function diffDays(startIso, endIso) {
        const d1 = parseIsoDate(startIso);
        const d2 = parseIsoDate(endIso);
        return Math.round((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    }

    function snapBackwardToPreferredDay(theoreticalNeedIso, preferredDeliveryDay) {
        const d = parseIsoDate(theoreticalNeedIso);
        const currentDow = d.getUTCDay();
        const targetDow = Number(preferredDeliveryDay);
        const diff = (currentDow - targetDow + 7) % 7;
        d.setUTCDate(d.getUTCDate() - diff);
        return formatIsoDate(d);
    }

    function parseBlackoutSet(rawStr) {
        const set = new Set();
        if (!rawStr) return set;
        rawStr.split(/[,;\r\n]+/).forEach((tok) => {
            const clean = tok.trim().split(/[|:]/)[0].trim();
            if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) {
                set.add(clean);
            }
        });
        return set;
    }

    function computeNodeDates(node, vendorLeadTime, actionBuffer, blackoutSet) {
        node.orderCutoffDate = addDays(node.actualDeliveryDate, -vendorLeadTime);
        node.unadjustedActionDate = addDays(node.orderCutoffDate, -actionBuffer);
        let adjusted = node.unadjustedActionDate;
        let subtracted = 0;
        while (blackoutSet.has(adjusted)) {
            adjusted = addDays(adjusted, -1);
            subtracted++;
        }
        node.actionDate = adjusted;
        node.holidayAdjusted = subtracted > 0;
        node.holidayAdjustedDays = subtracted;
    }

    function escapeHtml(val) {
        if (val === null || val === undefined) return '';
        return String(val)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // 4. Snapshot Fetch & Outbox Sync with Server
    async function refreshSnapshotFromServer() {
        if (!navigator.onLine) return null;
        try {
            const resp = await fetch('/api/offline/snapshot', {
                method: 'GET',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });
            if (resp.ok && !resp.redirected) {
                const snapshot = await resp.json();
                await saveSnapshotLocal(snapshot);
                return snapshot;
            }
        } catch (ignored) {}
        return null;
    }

    async function syncPendingOutbox() {
        const outbox = getOutboxLocal();
        if (isSyncing || outbox.length === 0 || !navigator.onLine) {
            updateNavbarStatus();
            return;
        }

        isSyncing = true;
        updateNavbarStatus();

        try {
            const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
            const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
            const headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            };
            if (csrfTokenMeta && csrfHeaderMeta) {
                headers[csrfHeaderMeta.getAttribute('content')] = csrfTokenMeta.getAttribute('content');
            }

            const resp = await fetch('/api/offline/sync', {
                method: 'POST',
                credentials: 'same-origin',
                headers: headers,
                body: JSON.stringify({ mutations: outbox })
            });

            if (resp.ok && !resp.redirected) {
                const result = await resp.json();
                if (result && result.success) {
                    await saveOutboxLocal([]);
                    if (result.snapshot) {
                        await saveSnapshotLocal(result.snapshot);
                    }
                    showSyncBanner(result.message || 'Offline changes synced to server.');
                    await renderPendingBatchesOnListPage();
                }
            }
        } catch (ignored) {
            // Still offline or unreachable
        } finally {
            isSyncing = false;
            updateNavbarStatus();
        }
    }

    function showSyncBanner(message) {
        let banner = document.getElementById('offlineSyncLiveToast');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'offlineSyncLiveToast';
            banner.className = 'alert alert-success alert-dismissible fade show position-fixed bottom-0 end-0 m-3 shadow';
            banner.style.zIndex = '1090';
            document.body.appendChild(banner);
        }
        banner.innerHTML =
            '<i class="bi bi-cloud-check-fill me-2"></i>' +
            '<span>' + escapeHtml(message) + '</span>' +
            '<button type="button" class="btn-close" data-bs-dismiss="alert"></button>';
    }

    // 5. Navbar & Offline Mode UI Toggle
    function updateNavbarStatus() {
        const online = navigator.onLine;
        if (document.body) {
            document.body.classList.toggle('is-offline', !online);
        }

        const badge = document.getElementById('pwaConnectionBadge');
        const syncBtn = document.getElementById('pwaSyncNowBtn');
        if (!badge) return;

        const outbox = getOutboxLocal();
        const pending = outbox.length;

        if (isSyncing) {
            badge.className = 'badge bg-info text-dark px-2 py-1';
            badge.innerHTML = '<i class="bi bi-arrow-repeat me-1"></i>Syncing (' + pending + ')...';
        } else if (!online) {
            badge.className = 'badge bg-warning text-dark px-2 py-1';
            badge.innerHTML =
                '<i class="bi bi-wifi-off me-1"></i>Offline Mode' +
                (pending > 0 ? ' (' + pending + ' pending)' : '');
        } else if (pending > 0) {
            badge.className = 'badge bg-warning text-dark px-2 py-1';
            badge.innerHTML = '<i class="bi bi-cloud-arrow-up me-1"></i>' + pending + ' Pending Sync';
        } else {
            badge.className = 'badge bg-light text-success border px-2 py-1';
            badge.innerHTML = '<i class="bi bi-cloud-check me-1"></i>Online';
        }

        if (syncBtn) {
            if (pending > 0 && online && !isSyncing) {
                syncBtn.classList.remove('d-none');
            } else {
                syncBtn.classList.add('d-none');
            }
        }
    }

    // 6. Pending & Cached Batches State Reconstruction (merges snapshot + outbox)
    async function getMergedBatchesMap() {
        const snapshot = (await getSnapshotLocal()) || { recentBatches: [], products: [], settings: {} };
        const outbox = getOutboxLocal();
        const productsMap = {};
        (snapshot.products || []).forEach((p) => {
            productsMap[Number(p.id)] = p;
        });

        const batchesByCode = {};

        // Seed from DOM cards on /batches if present
        document.querySelectorAll('.batch-card-col[data-batch-code]').forEach((col) => {
            const code = col.getAttribute('data-batch-code');
            if (!code) return;
            const prodId = Number(col.getAttribute('data-product-id') || 0);
            const p = productsMap[prodId] || {};
            batchesByCode[code] = {
                id: Number(col.getAttribute('data-batch-id') || 0),
                batchCode: code,
                productId: prodId,
                productName: col.getAttribute('data-product-name') || p.name || 'Product',
                batchDate: col.getAttribute('data-batch-date') || '',
                expiryDate: col.getAttribute('data-expiry-date') || '',
                totalUnits: Number(col.getAttribute('data-total-units') || 1),
                defaultExpiryDays: Number(col.getAttribute('data-default-expiry') || p.defaultExpiryDays || 5),
                pendingSync: false,
                isOfflineCreated: false,
                createdSeq: 0,
                deletedOffline: false,
                spoiledUnits: {}
            };
        });

        // Merge from cached snapshot.recentBatches
        (snapshot.recentBatches || []).forEach((b) => {
            if (!b || !b.batchCode) return;
            const p = productsMap[Number(b.productId)] || {};
            if (!batchesByCode[b.batchCode]) {
                batchesByCode[b.batchCode] = {
                    id: b.id || 0,
                    batchCode: b.batchCode,
                    productId: Number(b.productId || 0),
                    productName: b.productName || p.name || 'Product',
                    batchDate: b.batchDate || '',
                    expiryDate: b.expiryDate || '',
                    totalUnits: Number(b.totalUnits || 1),
                    defaultExpiryDays: Number(p.defaultExpiryDays || 5),
                    pendingSync: false,
                    isOfflineCreated: false,
                    createdSeq: 0,
                    deletedOffline: false,
                    spoiledUnits: b.spoiledUnits || {}
                };
            }
        });

        // Replay outbox mutations to compute live state
        let seqCounter = 1;
        outbox.forEach((m) => {
            if (!m || !m.mutationType) return;
            const payload = m.payload || {};
            const code = payload.batchCode;
            if (m.mutationType === 'CREATE_BATCH' && code) {
                const prodId = Number(payload.productId || 0);
                const p = productsMap[prodId] || {};
                const expDays = Number(p.defaultExpiryDays || 5);
                const bDate = payload.batchDate || formatIsoDate(new Date());
                batchesByCode[code] = {
                    id: 0,
                    batchCode: code,
                    productId: prodId,
                    productName: p.name || 'Product #' + prodId,
                    batchDate: bDate,
                    expiryDate: expDays ? addDays(bDate, expDays) : '',
                    totalUnits: Number(payload.quantity || 1),
                    defaultExpiryDays: expDays,
                    pendingSync: true,
                    isOfflineCreated: true,
                    createdSeq: seqCounter++,
                    deletedOffline: false,
                    spoiledUnits: {}
                };
            } else if (m.mutationType === 'UPDATE_BATCH' && code && batchesByCode[code]) {
                const target = batchesByCode[code];
                target.batchDate = payload.batchDate || target.batchDate;
                if (target.defaultExpiryDays) {
                    target.expiryDate = addDays(target.batchDate, target.defaultExpiryDays);
                }
                target.pendingSync = true;
            } else if (m.mutationType === 'DELETE_BATCH' && code && batchesByCode[code]) {
                batchesByCode[code].deletedOffline = true;
            } else if (m.mutationType === 'SPOIL_UNIT' && payload.unitUuid) {
                const uuid = payload.unitUuid;
                const lastDash = uuid.lastIndexOf('-');
                if (lastDash > 0) {
                    const bCode = uuid.substring(0, lastDash);
                    if (batchesByCode[bCode]) {
                        batchesByCode[bCode].spoiledUnits[uuid] = true;
                        batchesByCode[bCode].pendingSync = true;
                    }
                }
            }
        });

        return { batchesByCode, productsMap, settings: snapshot.settings || {} };
    }

    // 7. Render Pending Batches & Interactive Cards on /batches
    async function renderPendingBatchesOnListPage() {
        const grid = document.getElementById('batchesGridContainer');
        if (!grid) return;

        // Remove previously injected pending batch cards before re-rendering
        grid.querySelectorAll('.offline-pending-batch-col').forEach((el) => el.remove());

        const { batchesByCode } = await getMergedBatchesMap();
        const existingCodesInDom = new Set();

        // Update existing server-rendered cards if edited or deleted offline
        grid.querySelectorAll('.batch-card-col[data-batch-code]').forEach((col) => {
            const code = col.getAttribute('data-batch-code');
            existingCodesInDom.add(code);
            const state = batchesByCode[code];
            if (!state) return;

            if (state.deletedOffline) {
                col.classList.add('d-none');
                return;
            }
            col.classList.remove('d-none');

            const dateEl = col.querySelector('.batch-card-date');
            if (dateEl && state.batchDate) {
                dateEl.textContent = state.batchDate;
                col.setAttribute('data-batch-date', state.batchDate);
            }
            const expEl = col.querySelector('.batch-card-expiry');
            if (expEl && state.expiryDate) {
                expEl.textContent = state.expiryDate;
                col.setAttribute('data-expiry-date', state.expiryDate);
            }

            // Show Pending Sync badge if modified offline
            let pendingBadge = col.querySelector('.pending-sync-card-badge');
            if (state.pendingSync) {
                if (!pendingBadge) {
                    const headerRow = col.querySelector('.card-title')?.parentElement;
                    if (headerRow) {
                        pendingBadge = document.createElement('span');
                        pendingBadge.className = 'badge bg-warning text-dark ms-2 pending-sync-card-badge';
                        pendingBadge.innerHTML = '<i class="bi bi-cloud-arrow-up me-1"></i>Pending Sync';
                        headerRow.appendChild(pendingBadge);
                    }
                }
            } else if (pendingBadge) {
                pendingBadge.remove();
            }
        });

        // Prepend cards ONLY for newly created offline batches not yet in server DOM
        const newOfflineBatches = Object.values(batchesByCode).filter(
            (b) => b.isOfflineCreated && !b.deletedOffline && !existingCodesInDom.has(b.batchCode)
        );

        newOfflineBatches.forEach((b) => {
            const col = document.createElement('div');
            col.className = 'col-12 col-md-6 col-lg-4 batch-card-col offline-pending-batch-col';
            col.setAttribute('data-batch-id', '0');
            col.setAttribute('data-batch-code', b.batchCode);
            col.setAttribute('data-product-id', String(b.productId));
            col.setAttribute('data-product-name', b.productName);
            col.setAttribute('data-batch-date', b.batchDate);
            col.setAttribute('data-expiry-date', b.expiryDate || '');
            col.setAttribute('data-total-units', String(b.totalUnits));
            col.setAttribute('data-created-seq', String(b.createdSeq || 0));
            col.innerHTML = `
                <a href="#" class="text-decoration-none batch-card-link" data-offline-batch="${escapeHtml(b.batchCode)}">
                    <div class="card h-100 border-warning shadow-sm card-hover-effect" style="border-width: 2px;">
                        <div class="card-body">
                            <div class="d-flex justify-content-between mb-3">
                                <div class="w-100">
                                    <div class="d-flex justify-content-between align-items-center mb-1">
                                        <h5 class="card-title fw-bold text-dark mb-0">${escapeHtml(b.productName)}</h5>
                                        <span class="badge bg-warning text-dark ms-2 pending-sync-card-badge">
                                            <i class="bi bi-cloud-arrow-up me-1"></i>Pending Sync
                                        </span>
                                    </div>
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span class="badge bg-light text-secondary border font-monospace">${escapeHtml(b.batchCode)}</span>
                                        <small class="text-muted"><i class="bi bi-calendar3 me-1"></i> <span class="batch-card-date">${escapeHtml(b.batchDate)}</span></small>
                                    </div>
                                </div>
                            </div>
                            <hr class="my-2 text-muted opacity-25">
                            <div class="d-flex justify-content-between align-items-center mt-2">
                                <span class="badge bg-success bg-opacity-10 text-success border border-success px-3">
                                    <i class="bi bi-box-seam me-1"></i> <span>${escapeHtml(b.totalUnits)}</span> Units
                                </span>
                                <small class="text-muted d-flex align-items-center">
                                    <i class="bi bi-hourglass-split me-1"></i> Exp: <span class="batch-card-expiry">${escapeHtml(b.expiryDate || '—')}</span>
                                </small>
                            </div>
                        </div>
                    </div>
                </a>
            `;
            grid.prepend(col);
        });

        // Sort all visible batch cards in strict latest-first order:
        // 1) batchDate DESC, 2) offline createdSeq DESC, 3) server batchId DESC, 4) batchCode DESC
        const allCards = Array.from(grid.querySelectorAll('.batch-card-col'));
        allCards.sort((a, b) => {
            const dateA = a.getAttribute('data-batch-date') || '';
            const dateB = b.getAttribute('data-batch-date') || '';
            if (dateA !== dateB) {
                return dateB.localeCompare(dateA);
            }
            const seqA = Number(a.getAttribute('data-created-seq') || 0);
            const seqB = Number(b.getAttribute('data-created-seq') || 0);
            if (seqA !== seqB) {
                return seqB - seqA;
            }
            const idA = Number(a.getAttribute('data-batch-id') || 0);
            const idB = Number(b.getAttribute('data-batch-id') || 0);
            if (idA !== idB) {
                return idB - idA;
            }
            const codeA = a.getAttribute('data-batch-code') || '';
            const codeB = b.getAttribute('data-batch-code') || '';
            return codeB.localeCompare(codeA);
        });
        allCards.forEach((card) => grid.appendChild(card));

        const emptyState = document.getElementById('batchesEmptyState');
        if (emptyState && (newOfflineBatches.length > 0 || existingCodesInDom.size > 0)) {
            emptyState.classList.add('d-none');
        }

        // Bind click handlers so clicking ANY Pending Sync card (or any card while offline) opens the full Batch Operations Modal
        grid.querySelectorAll('.batch-card-col').forEach((col) => {
            const link = col.querySelector('.batch-card-link');
            const code = col.getAttribute('data-batch-code');
            if (!link || !code || link.dataset.boundOfflineModal === 'true') return;
            link.dataset.boundOfflineModal = 'true';

            link.addEventListener('click', async function (e) {
                const { batchesByCode: currentMap } = await getMergedBatchesMap();
                const batchObj = currentMap[code];
                const isPendingOnly = col.classList.contains('offline-pending-batch-col') || (batchObj && batchObj.pendingSync);
                if (!navigator.onLine || isPendingOnly) {
                    e.preventDefault();
                    openOfflineBatchModal(code);
                }
            });
        });
    }

    // 8. Full Batch Detail & Operations Modal (View, Edit, Delete, Print All, Print Range, Spoil Unit, Reprint Single Unit)
    async function openOfflineBatchModal(batchCode) {
        const { batchesByCode } = await getMergedBatchesMap();
        const batch = batchesByCode[batchCode];
        if (!batch) return;

        activeModalBatchCode = batchCode;

        document.getElementById('obmBatchCode').textContent = batch.batchCode;
        document.getElementById('obmProductName').textContent = batch.productName;
        document.getElementById('obmBatchDate').textContent = batch.batchDate || '—';
        document.getElementById('obmTotalUnits').textContent = String(batch.totalUnits);
        document.getElementById('obmExpiryDate').textContent = batch.expiryDate || '—';
        document.getElementById('obmEditDateInput').value = batch.batchDate || formatIsoDate(new Date());
        document.getElementById('obmRangeStart').value = '1';
        document.getElementById('obmRangeEnd').value = String(batch.totalUnits);
        document.getElementById('obmEditSection').classList.add('d-none');
        document.getElementById('obmRangeSection').classList.add('d-none');

        const syncBadge = document.getElementById('obmSyncBadge');
        if (syncBadge) {
            if (batch.pendingSync) {
                syncBadge.className = 'badge bg-warning text-dark';
                syncBadge.innerHTML = '<i class="bi bi-cloud-arrow-up me-1"></i>Pending Sync';
            } else {
                syncBadge.className = 'badge bg-success';
                syncBadge.innerHTML = '<i class="bi bi-check-circle me-1"></i>Synced';
            }
        }

        const countBadge = document.getElementById('obmUnitsCountBadge');
        if (countBadge) {
            countBadge.textContent = batch.totalUnits + ' items';
        }

        renderOfflineModalUnitsTable(batch);

        const modalEl = document.getElementById('offlineBatchManageModal');
        if (modalEl && window.bootstrap) {
            const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
        }
    }

    function renderOfflineModalUnitsTable(batch) {
        const tbody = document.getElementById('obmUnitsTableBody');
        if (!tbody) return;

        const rows = [];
        for (let i = 1; i <= batch.totalUnits; i++) {
            const uuid = batch.batchCode + '-' + i;
            const isSpoiled = Boolean(batch.spoiledUnits && batch.spoiledUnits[uuid]);
            const statusBadge = isSpoiled
                ? '<span class="badge bg-secondary bg-opacity-10 text-secondary border border-secondary">Spoiled</span>'
                : '<span class="badge bg-success bg-opacity-10 text-success border border-success">Available</span>';

            const spoilBtn = !isSpoiled
                ? `<button type="button" class="btn btn-sm btn-link text-secondary p-0 ms-2" title="Mark as Spoiled" onclick="window.MoStockOfflineSync.spoilOfflineUnit('${escapeHtml(batch.batchCode)}', '${escapeHtml(uuid)}')">
                       <i class="bi bi-x-circle"></i>
                   </button>`
                : '';

            rows.push(`
                <tr>
                    <td class="ps-3 font-monospace text-muted">${escapeHtml(uuid)}</td>
                    <td class="text-end pe-3">
                        ${statusBadge}
                        ${spoilBtn}
                        <button type="button" class="btn btn-sm btn-link text-dark p-0 ms-2" title="Reprint Single Label" onclick="window.MoStockOfflineSync.printOfflineBatchUnits('${escapeHtml(batch.batchCode)}', ${i}, ${i})">
                            <i class="bi bi-printer"></i>
                        </button>
                    </td>
                </tr>
            `);
        }
        tbody.innerHTML = rows.join('');
    }

    function toggleOfflineBatchEdit() {
        const el = document.getElementById('obmEditSection');
        if (el) el.classList.toggle('d-none');
    }

    function toggleOfflinePrintRange() {
        const el = document.getElementById('obmRangeSection');
        if (el) el.classList.toggle('d-none');
    }

    async function saveOfflineBatchEdit() {
        if (!activeModalBatchCode) return;
        const newDate = document.getElementById('obmEditDateInput').value;
        if (!newDate) return;

        const outbox = getOutboxLocal();
        const createEntry = outbox.find(
            (m) => m.mutationType === 'CREATE_BATCH' && m.payload && m.payload.batchCode === activeModalBatchCode
        );

        if (createEntry) {
            createEntry.payload.batchDate = newDate;
            await saveOutboxLocal(outbox);
        } else {
            const { batchesByCode } = await getMergedBatchesMap();
            const b = batchesByCode[activeModalBatchCode];
            await enqueueMutation('UPDATE_BATCH', {
                batchId: b && b.id ? String(b.id) : '',
                batchCode: activeModalBatchCode,
                batchDate: newDate
            });
        }

        // Also update snapshot.recentBatches if present
        const snapshot = await getSnapshotLocal();
        if (snapshot && Array.isArray(snapshot.recentBatches)) {
            const sb = snapshot.recentBatches.find((x) => x.batchCode === activeModalBatchCode);
            if (sb) {
                sb.batchDate = newDate;
                const prod = (snapshot.products || []).find((p) => Number(p.id) === Number(sb.productId));
                if (prod && prod.defaultExpiryDays) {
                    sb.expiryDate = addDays(newDate, prod.defaultExpiryDays);
                }
                sb.pendingSync = true;
                await saveSnapshotLocal(snapshot);
            }
        }

        await renderPendingBatchesOnListPage();
        await openOfflineBatchModal(activeModalBatchCode);
        showSyncBanner('Batch ' + activeModalBatchCode + ' harvest date updated (queued for sync).');
        if (navigator.onLine) {
            await syncPendingOutbox();
        }
    }

    async function deleteActiveOfflineBatch() {
        if (!activeModalBatchCode) return;
        if (!confirm('Are you sure you want to delete batch ' + activeModalBatchCode + '?')) return;

        const targetCode = activeModalBatchCode;
        const outbox = getOutboxLocal();
        const hadPendingCreate = outbox.some(
            (m) => m.mutationType === 'CREATE_BATCH' && m.payload && m.payload.batchCode === targetCode
        );

        if (hadPendingCreate) {
            // Remove all pending mutations for this unsynced offline batch
            const filtered = outbox.filter((m) => !(m.payload && m.payload.batchCode === targetCode));
            await saveOutboxLocal(filtered);
        } else {
            const { batchesByCode } = await getMergedBatchesMap();
            const b = batchesByCode[targetCode];
            await enqueueMutation('DELETE_BATCH', {
                batchId: b && b.id ? String(b.id) : '',
                batchCode: targetCode
            });
        }

        const snapshot = await getSnapshotLocal();
        if (snapshot && Array.isArray(snapshot.recentBatches)) {
            snapshot.recentBatches = snapshot.recentBatches.filter((x) => x.batchCode !== targetCode);
            await saveSnapshotLocal(snapshot);
        }

        const modalEl = document.getElementById('offlineBatchManageModal');
        if (modalEl && window.bootstrap) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }

        await renderPendingBatchesOnListPage();
        showSyncBanner('Batch ' + targetCode + ' deleted.');
        if (navigator.onLine) {
            await syncPendingOutbox();
        }
    }

    async function spoilOfflineUnit(batchCode, unitUuid) {
        if (!confirm('Mark unit ' + unitUuid + ' as SPOILED?')) return;
        await enqueueMutation('SPOIL_UNIT', {
            batchCode: batchCode,
            unitUuid: unitUuid
        });
        await openOfflineBatchModal(batchCode);
        showSyncBanner('Unit ' + unitUuid + ' marked as SPOILED (queued for sync).');
        if (navigator.onLine) {
            await syncPendingOutbox();
        }
    }

    async function printOfflineBatchUnits(batchCode, seqStart, seqEnd) {
        const { batchesByCode, productsMap, settings } = await getMergedBatchesMap();
        const batch = batchesByCode[batchCode];
        if (!batch) return;

        const start = Math.max(1, Number(seqStart || 1));
        const end = Math.min(batch.totalUnits, Number(seqEnd || batch.totalUnits));
        const unitCodes = [];
        for (let i = start; i <= end; i++) {
            unitCodes.push(batch.batchCode + '-' + i);
        }

        const product = productsMap[Number(batch.productId)] || {
            id: batch.productId,
            name: batch.productName
        };

        const printJob = {
            manualMode: false,
            batchCode: batch.batchCode,
            batchDate: batch.batchDate,
            expiryDate: batch.expiryDate,
            product: product,
            settings: settings,
            unitCodes: unitCodes,
            backUrl: '/batches'
        };
        localStorage.setItem(PRINT_JOB_KEY, JSON.stringify(printJob));
        await openOfflinePrintSheet();
    }

    function printActiveOfflineBatchAll() {
        if (!activeModalBatchCode) return;
        const modalEl = document.getElementById('offlineBatchManageModal');
        if (modalEl && window.bootstrap) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }
        printOfflineBatchUnits(activeModalBatchCode, 1, 5000);
    }

    function printActiveOfflineBatchRange() {
        if (!activeModalBatchCode) return;
        const start = Number(document.getElementById('obmRangeStart').value || 1);
        const end = Number(document.getElementById('obmRangeEnd').value || start);
        const modalEl = document.getElementById('offlineBatchManageModal');
        if (modalEl && window.bootstrap) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }
        printOfflineBatchUnits(activeModalBatchCode, start, end);
    }

    async function openOfflinePrintSheet() {
        if (navigator.onLine) {
            window.location.href = '/batches/offline-print';
            return;
        }
        await navigateOfflineTo('/batches/offline-print', true);
    }

    async function buildSynthesizedFallbackHtml(targetPath) {
        const snapshot = (await getSnapshotLocal()) || { products: [], settings: {} };
        const productsOptions = (snapshot.products || [])
            .map((p) => `<option value="${escapeHtml(p.id)}">${escapeHtml(p.name)}</option>`)
            .join('');

        if (targetPath === '/batches/create') {
            const container = document.querySelector('.container-fluid.mt-3');
            if (container) {
                const cloneDoc = document.documentElement.cloneNode(true);
                const mainDiv = cloneDoc.querySelector('.container-fluid.mt-3');
                if (mainDiv) {
                    const tomorrow = addDays(formatIsoDate(new Date()), 1);
                    mainDiv.innerHTML = `
                        <div class="row justify-content-center">
                            <div class="col-12 col-md-8 col-lg-6">
                                <div class="d-flex align-items-center mb-4">
                                    <a href="/dashboard" class="btn btn-outline-secondary btn-sm me-3 border-0"><i class="bi bi-arrow-left fs-5"></i></a>
                                    <h3 class="mb-0 text-dark">Create Harvest Batch</h3>
                                </div>
                                <div class="card shadow-lg border-0">
                                    <div class="card-body p-4">
                                        <form action="/batches/save" method="post">
                                            <h6 class="text-uppercase text-muted fw-bold small mb-4">Batch Information</h6>
                                            <div class="mb-3">
                                                <label class="form-label">Product</label>
                                                <select class="form-select searchable" id="product" name="productId" required placeholder="Select Product...">
                                                    <option value="">Choose product...</option>
                                                    ${productsOptions}
                                                </select>
                                            </div>
                                            <div class="mb-3">
                                                <label class="form-label text-muted small">Quantity (Units/Boxes)</label>
                                                <div class="input-group">
                                                    <span class="input-group-text bg-transparent border-end-0 text-muted"><i class="bi bi-boxes"></i></span>
                                                    <input type="number" class="form-control form-control-lg border-start-0 ps-0" name="quantity" min="1" max="5000" value="100" required>
                                                </div>
                                            </div>
                                            <div class="mb-4">
                                                <label class="form-label text-muted small">Harvest Date</label>
                                                <input type="date" class="form-control" name="batchDate" max="${tomorrow}">
                                                <div class="form-text text-muted opacity-50">Leave empty to use today's date.</div>
                                            </div>
                                            <div class="d-grid gap-2 mt-5">
                                                <button type="submit" class="btn btn-success btn-lg shadow-sm">
                                                    <i class="bi bi-upc-scan me-2"></i>Generate Batch &amp; IDs
                                                </button>
                                                <a href="/dashboard" class="btn btn-link text-muted text-decoration-none">Cancel</a>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                    return '<!DOCTYPE html>\n' + cloneDoc.outerHTML;
                }
            }
        }
        return null;
    }

    async function navigateOfflineTo(rawUrl, pushHistory) {
        if (navigator.onLine) {
            window.location.href = rawUrl;
            return;
        }
        const targetPath = normalizeOfflinePath(rawUrl);
        let html = await getPageHtmlLocal(targetPath);
        if (!html) {
            html = await buildSynthesizedFallbackHtml(targetPath);
        }
        if (!html) {
            showSyncBanner('This page has not been cached yet. Reconnect once to cache all offline pages.');
            return;
        }

        // Hide any open Bootstrap modal backdrop before swapping DOM
        document.querySelectorAll('.modal-backdrop').forEach((el) => el.remove());
        document.body.classList.remove('modal-open');
        document.body.style.removeProperty('overflow');
        document.body.style.removeProperty('padding-right');

        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');

        // Clean any pre-existing Select2 wrappers inside parsed HTML so Select2 can initialize cleanly
        doc.body.querySelectorAll('.select2-container').forEach((el) => el.remove());
        doc.body.querySelectorAll('select.select2-hidden-accessible').forEach((sel) => {
            sel.classList.remove('select2-hidden-accessible');
            sel.removeAttribute('data-select2-id');
            sel.removeAttribute('aria-hidden');
            sel.removeAttribute('tabindex');
        });

        if (doc.title) {
            document.title = doc.title;
        }

        // Copy page-specific inline <style> blocks from parsed <head>
        document.head.querySelectorAll('style[data-offline-injected="true"]').forEach((s) => s.remove());
        doc.head.querySelectorAll('style').forEach((styleEl) => {
            const clonedStyle = document.createElement('style');
            clonedStyle.setAttribute('data-offline-injected', 'true');
            if (styleEl.id) clonedStyle.id = styleEl.id;
            clonedStyle.textContent = styleEl.textContent;
            document.head.appendChild(clonedStyle);
        });

        // Swap body attributes & innerHTML
        document.body.className = doc.body.className || '';
        if (doc.body.id) {
            document.body.id = doc.body.id;
        } else {
            document.body.removeAttribute('id');
        }
        document.body.innerHTML = doc.body.innerHTML;

        if (pushHistory !== false) {
            try {
                window.history.pushState({ offlinePath: targetPath }, '', targetPath);
            } catch (ignored) {}
        }

        // Ensure product dropdowns have options from cached snapshot if needed
        const snapshot = await getSnapshotLocal();
        if (snapshot && Array.isArray(snapshot.products) && snapshot.products.length > 0) {
            document.querySelectorAll('select[name="productId"]').forEach((sel) => {
                if (sel.options.length <= 1) {
                    snapshot.products.forEach((p) => {
                        const opt = document.createElement('option');
                        opt.value = String(p.id);
                        opt.textContent = p.name;
                        sel.appendChild(opt);
                    });
                }
            });
        }

        // Execute inline scripts from the target page (shimming DOMContentLoaded so handlers fire immediately)
        const origAddEventListener = document.addEventListener.bind(document);
        document.addEventListener = function (type, listener, options) {
            if (type === 'DOMContentLoaded' && typeof listener === 'function') {
                try {
                    listener(new Event('DOMContentLoaded'));
                } catch (ignored) {}
                return;
            }
            return origAddEventListener(type, listener, options);
        };
        try {
            doc.body.querySelectorAll('script:not([src])').forEach((scr) => {
                try {
                    const newScr = document.createElement('script');
                    newScr.textContent = scr.textContent;
                    document.body.appendChild(newScr);
                } catch (ignored) {}
            });
        } finally {
            document.addEventListener = origAddEventListener;
        }

        await initPageOfflineFeatures();
        if (targetPath === '/batches/offline-print' && typeof window.renderOfflinePrintJob === 'function') {
            window.renderOfflinePrintJob();
        }
        window.scrollTo(0, 0);
    }

    // 9. Form Interceptors for Create Batch, Detail Page Actions, Manual Labels, ARPS & Settings
    function bindOfflineFormInterceptors() {
        // A. Create Batch Form (/batches/save)
        const createBatchForm = document.querySelector('form[action$="/batches/save"]');
        if (createBatchForm) {
            createBatchForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(createBatchForm);
                const productId = Number(formData.get('productId'));
                const quantity = Number(formData.get('quantity') || 1);
                const rawBatchDate = formData.get('batchDate');
                const batchDate = rawBatchDate && String(rawBatchDate).trim()
                    ? String(rawBatchDate).trim()
                    : formatIsoDate(new Date());

                const snapshot = (await getSnapshotLocal()) || { products: [], recentBatches: [], settings: {} };
                const product = (snapshot.products || []).find((p) => Number(p.id) === productId) || {
                    id: productId,
                    name: 'Product #' + productId,
                    defaultExpiryDays: 5
                };

                const dateCompact = batchDate.replace(/-/g, '');
                const uniqueSuffix = String(Date.now()).substring(6);
                const batchCode = 'B-' + dateCompact + '-' + uniqueSuffix;
                const expiryDate = product.defaultExpiryDays
                    ? addDays(batchDate, product.defaultExpiryDays)
                    : null;

                await enqueueMutation('CREATE_BATCH', {
                    productId: String(productId),
                    quantity: String(quantity),
                    batchDate: batchDate,
                    batchCode: batchCode
                });

                // Also add to snapshot.recentBatches with pendingSync: true so it appears immediately on /batches
                snapshot.recentBatches = snapshot.recentBatches || [];
                snapshot.recentBatches.unshift({
                    id: 0,
                    batchCode: batchCode,
                    productId: productId,
                    productName: product.name,
                    batchDate: batchDate,
                    expiryDate: expiryDate,
                    totalUnits: quantity,
                    pendingSync: true
                });
                await saveSnapshotLocal(snapshot);

                const unitCodes = [];
                for (let i = 1; i <= quantity; i++) {
                    unitCodes.push(batchCode + '-' + i);
                }

                const printJob = {
                    manualMode: false,
                    batchCode: batchCode,
                    batchDate: batchDate,
                    expiryDate: expiryDate,
                    product: product,
                    settings: snapshot.settings || {},
                    unitCodes: unitCodes,
                    backUrl: '/batches'
                };
                localStorage.setItem(PRINT_JOB_KEY, JSON.stringify(printJob));
                await openOfflinePrintSheet();
            });
        }

        // B. Online Batch Detail Page (/batches/{id}) Edit, Delete, Print Range & Spoil Unit Forms when Offline
        const editBatchDetailForm = document.querySelector('#editBatchModal form');
        if (editBatchDetailForm) {
            editBatchDetailForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();
                const action = editBatchDetailForm.getAttribute('action') || '';
                const match = action.match(/\/batches\/(\d+)\/update/);
                const batchId = match ? match[1] : '';
                const newDate = new FormData(editBatchDetailForm).get('batchDate');
                const codeEl = document.querySelector('h3 span.text-gradient');
                const batchCode = codeEl ? codeEl.textContent.trim() : '';

                await enqueueMutation('UPDATE_BATCH', {
                    batchId: String(batchId),
                    batchCode: batchCode,
                    batchDate: String(newDate)
                });
                showSyncBanner('Batch harvest date updated offline (queued for sync).');
            });
        }

        const deleteBatchDetailForm = document.querySelector('#deleteBatchModal form');
        if (deleteBatchDetailForm) {
            deleteBatchDetailForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();
                const action = deleteBatchDetailForm.getAttribute('action') || '';
                const match = action.match(/\/batches\/(\d+)\/delete/);
                const batchId = match ? match[1] : '';
                const codeEl = document.querySelector('h3 span.text-gradient');
                const batchCode = codeEl ? codeEl.textContent.trim() : '';

                await enqueueMutation('DELETE_BATCH', {
                    batchId: String(batchId),
                    batchCode: batchCode
                });
                await navigateOfflineTo('/batches', true);
            });
        }

        document.querySelectorAll('form[action*="/batches/units/"][action$="/spoil"]').forEach(function (spoilForm) {
            spoilForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();
                const row = spoilForm.closest('tr');
                const uuidEl = row ? row.querySelector('td.font-monospace') : null;
                const unitUuid = uuidEl ? uuidEl.textContent.trim() : '';
                if (unitUuid) {
                    await enqueueMutation('SPOIL_UNIT', { unitUuid: unitUuid });
                    showSyncBanner('Unit ' + unitUuid + ' marked as SPOILED offline.');
                }
            });
        });

        // C. Generate Manual Labels Form (/batches/manual-labels/generate)
        const manualGenForm = document.getElementById('generateManualForm');
        if (manualGenForm) {
            manualGenForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(manualGenForm);
                const productId = Number(formData.get('productId'));
                const quantity = Number(formData.get('quantity') || 1);

                const snapshot = (await getSnapshotLocal()) || {
                    products: [],
                    settings: {},
                    manualLabelLastSequence: 0
                };
                const product = (snapshot.products || []).find((p) => Number(p.id) === productId) || {
                    id: productId,
                    name: 'Product #' + productId
                };

                const lastSeq = Number(snapshot.manualLabelLastSequence || 0);
                const seqStart = lastSeq + 1;
                const seqEnd = lastSeq + quantity;
                snapshot.manualLabelLastSequence = seqEnd;
                await saveSnapshotLocal(snapshot);

                await enqueueMutation('ALLOCATE_MANUAL_LABELS', {
                    productId: String(productId),
                    quantity: String(quantity),
                    seqStart: String(seqStart),
                    seqEnd: String(seqEnd)
                });

                const unitCodes = [];
                for (let s = seqStart; s <= seqEnd; s++) {
                    unitCodes.push('M-' + String(s).padStart(4, '0'));
                }

                const printJob = {
                    manualMode: true,
                    batchCode: unitCodes[0] + (unitCodes.length > 1 ? ' to ' + unitCodes[unitCodes.length - 1] : ''),
                    batchDate: null,
                    expiryDate: null,
                    product: product,
                    settings: snapshot.settings || {},
                    unitCodes: unitCodes,
                    backUrl: '/batches/manual-labels'
                };
                localStorage.setItem(PRINT_JOB_KEY, JSON.stringify(printJob));
                await openOfflinePrintSheet();
            });
        }

        // D. Reprint Manual Labels Form (/batches/manual-labels/print)
        const manualReprintForm = document.querySelector('form[action$="/batches/manual-labels/print"]');
        if (manualReprintForm) {
            manualReprintForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(manualReprintForm);
                const productId = Number(formData.get('productId'));
                const seqStart = Number(formData.get('seqStart') || 1);
                const seqEnd = Number(formData.get('seqEnd') || seqStart);

                const snapshot = (await getSnapshotLocal()) || { products: [], settings: {} };
                const product = (snapshot.products || []).find((p) => Number(p.id) === productId) || {
                    id: productId,
                    name: 'Product #' + productId
                };

                const unitCodes = [];
                for (let s = seqStart; s <= seqEnd; s++) {
                    unitCodes.push('M-' + String(s).padStart(4, '0'));
                }

                const printJob = {
                    manualMode: true,
                    batchCode: unitCodes[0] + (unitCodes.length > 1 ? ' to ' + unitCodes[unitCodes.length - 1] : ''),
                    batchDate: null,
                    expiryDate: null,
                    product: product,
                    settings: snapshot.settings || {},
                    unitCodes: unitCodes,
                    backUrl: '/batches/manual-labels'
                };
                localStorage.setItem(PRINT_JOB_KEY, JSON.stringify(printJob));
                await openOfflinePrintSheet();
            });
        }

        // E. Procurement Scheduler Override Forms (/procurement/schedules/{id}/override)
        document.querySelectorAll('.override-delivery-form').forEach(function (form) {
            form.addEventListener('submit', async function (e) {
                if (navigator.onLine || e.defaultPrevented) return;
                e.preventDefault();

                const formData = new FormData(form);
                const cycleIndex = Number(formData.get('cycleIndex'));
                const newDeliveryDate = String(formData.get('newDeliveryDate')).trim();
                const actionUrl = form.getAttribute('action') || '';
                const match = actionUrl.match(/\/schedules\/(\d+)\/override/);
                const scheduleId = match ? Number(match[1]) : 0;

                const snapshot = (await getSnapshotLocal()) || { procurementSchedules: [], settings: {} };
                const schedule = (snapshot.procurementSchedules || []).find((s) => Number(s.id) === scheduleId);

                await enqueueMutation('OVERRIDE_PROCUREMENT_DATE', {
                    scheduleId: String(scheduleId),
                    scheduleName: schedule ? schedule.scheduleName : '',
                    cycleIndex: String(cycleIndex),
                    newDeliveryDate: newDeliveryDate
                });

                if (schedule && Array.isArray(schedule.nodes)) {
                    const blackoutSet = parseBlackoutSet((snapshot.settings || {}).procurement_blackout_dates || '');
                    const targetNode = schedule.nodes[cycleIndex - 1];
                    const shiftedDays = diffDays(targetNode.actualDeliveryDate, newDeliveryDate);

                    targetNode.actualDeliveryDate = newDeliveryDate;
                    targetNode.manualOverride = true;
                    if (cycleIndex === 1) {
                        targetNode.theoreticalNeedDate = newDeliveryDate;
                        targetNode.gapDays = schedule.supplyLastingDays;
                        targetNode.stockoutRisk = false;
                    } else {
                        const prevNode = schedule.nodes[cycleIndex - 2];
                        targetNode.gapDays = diffDays(prevNode.actualDeliveryDate, newDeliveryDate);
                        targetNode.stockoutRisk = targetNode.gapDays > schedule.supplyLastingDays;
                    }
                    computeNodeDates(targetNode, schedule.vendorLeadTime, schedule.actionBuffer, blackoutSet);

                    for (let n = cycleIndex + 1; n <= 12; n++) {
                        const prev = schedule.nodes[n - 2];
                        const curr = schedule.nodes[n - 1];
                        curr.theoreticalNeedDate = addDays(prev.actualDeliveryDate, schedule.supplyLastingDays);
                        curr.actualDeliveryDate = snapBackwardToPreferredDay(curr.theoreticalNeedDate, schedule.preferredDeliveryDay);
                        curr.manualOverride = false;
                        curr.gapDays = diffDays(prev.actualDeliveryDate, curr.actualDeliveryDate);
                        curr.stockoutRisk = curr.gapDays > schedule.supplyLastingDays;
                        computeNodeDates(curr, schedule.vendorLeadTime, schedule.actionBuffer, blackoutSet);
                    }
                    await saveSnapshotLocal(snapshot);

                    showSyncBanner(
                        'Saved offline (queued for sync): Delivery shifted by ' +
                        shiftedDays +
                        ' days. Subsequent 12 months recalculated.'
                    );
                } else {
                    showSyncBanner('Delivery override queued offline. Will recalculate on reconnect.');
                }
            });
        });

        // F. Procurement Create Schedule Form (/procurement/schedules)
        const createScheduleForm = document.querySelector('form[action$="/procurement/schedules"]');
        if (createScheduleForm) {
            createScheduleForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(createScheduleForm);
                await enqueueMutation('CREATE_PROCUREMENT_SCHEDULE', {
                    scheduleName: String(formData.get('scheduleName') || ''),
                    supplyLastingDays: String(formData.get('supplyLastingDays') || '28'),
                    vendorLeadTime: String(formData.get('vendorLeadTime') || '15'),
                    actionBuffer: String(formData.get('actionBuffer') || '3'),
                    preferredDeliveryDay: String(formData.get('preferredDeliveryDay') || '6'),
                    initialDeliveryDate: String(formData.get('initialDeliveryDate') || '')
                });

                const modalEl = document.getElementById('newScheduleModal');
                if (modalEl && window.bootstrap) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                showSyncBanner('Schedule creation queued offline. It will sync automatically when online.');
            });
        }

        // G. Procurement Blackout Dates Form (/procurement/blackout-dates)
        const blackoutForm = document.querySelector('form[action$="/procurement/blackout-dates"]');
        if (blackoutForm) {
            blackoutForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(blackoutForm);
                const blackoutDates = String(formData.get('blackoutDates') || '');
                const snapshot = (await getSnapshotLocal()) || { settings: {} };
                snapshot.settings = snapshot.settings || {};
                snapshot.settings.procurement_blackout_dates = blackoutDates;
                await saveSnapshotLocal(snapshot);

                await enqueueMutation('UPDATE_BLACKOUT_DATES', {
                    blackoutDates: blackoutDates
                });

                const modalEl = document.getElementById('blackoutDatesModal');
                if (modalEl && window.bootstrap) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                showSyncBanner('Holiday blackout dates saved offline and queued for sync.');
            });
        }

        // H. System Settings Form (/admin/settings)
        const settingsForm = document.querySelector('form[action$="/admin/settings"]');
        if (settingsForm) {
            settingsForm.addEventListener('submit', async function (e) {
                if (navigator.onLine) return;
                e.preventDefault();

                const formData = new FormData(settingsForm);
                const payload = {};
                const snapshot = (await getSnapshotLocal()) || { settings: {} };
                snapshot.settings = snapshot.settings || {};

                formData.forEach((value, key) => {
                    if (!key.startsWith('_') && key !== 'csrf') {
                        payload[key] = String(value);
                        snapshot.settings[key] = String(value);
                    }
                });

                await saveSnapshotLocal(snapshot);
                await enqueueMutation('UPDATE_SETTINGS', payload);
                showSyncBanner('Settings saved offline for label printing & ARPS, and queued for server sync.');
            });
        }
    }

    async function initPageOfflineFeatures() {
        updateNavbarStatus();
        bindOfflineFormInterceptors();
        await renderPendingBatchesOnListPage();
    }

    // Intercept offline link navigations & F5/Ctrl+R refreshes so offline navigation works even on plain HTTP LAN servers
    document.addEventListener('click', function (e) {
        if (navigator.onLine || e.defaultPrevented) return;
        const anchor = e.target.closest('a[href]');
        if (!anchor) return;
        const href = anchor.getAttribute('href') || '';
        if (
            !href ||
            href.startsWith('#') ||
            href.startsWith('javascript:') ||
            anchor.hasAttribute('data-bs-toggle') ||
            anchor.classList.contains('batch-card-link')
        ) {
            return;
        }
        try {
            const url = new URL(href, window.location.origin);
            if (url.origin !== window.location.origin) return;
            const norm = normalizeOfflinePath(url.pathname);
            if (OFFLINE_APP_PAGES.includes(norm)) {
                e.preventDefault();
                navigateOfflineTo(norm, true);
            }
        } catch (ignored) {}
    });

    window.addEventListener('keydown', function (e) {
        if (navigator.onLine) return;
        const isF5 = e.key === 'F5';
        const isCtrlR = (e.ctrlKey || e.metaKey) && (e.key === 'r' || e.key === 'R');
        if (isF5 || isCtrlR) {
            e.preventDefault();
            navigateOfflineTo(window.location.pathname, false);
        }
    });

    window.addEventListener('popstate', function () {
        if (!navigator.onLine) {
            navigateOfflineTo(window.location.pathname, false);
        }
    });

    // 10. Public API & Lifecycle Initialization
    window.MoStockOfflineSync = {
        syncNow: syncPendingOutbox,
        refreshSnapshot: refreshSnapshotFromServer,
        getOutbox: getOutboxLocal,
        navigateOfflineTo: navigateOfflineTo,
        openOfflineBatchModal: openOfflineBatchModal,
        toggleOfflineBatchEdit: toggleOfflineBatchEdit,
        toggleOfflinePrintRange: toggleOfflinePrintRange,
        saveOfflineBatchEdit: saveOfflineBatchEdit,
        deleteActiveOfflineBatch: deleteActiveOfflineBatch,
        spoilOfflineUnit: spoilOfflineUnit,
        printOfflineBatchUnits: printOfflineBatchUnits,
        printActiveOfflineBatchAll: printActiveOfflineBatchAll,
        printActiveOfflineBatchRange: printActiveOfflineBatchRange
    };

    window.addEventListener('online', function () {
        updateNavbarStatus();
        syncPendingOutbox();
        warmAllOfflineCaches();
    });

    window.addEventListener('offline', function () {
        updateNavbarStatus();
        renderPendingBatchesOnListPage();
    });

    document.addEventListener('DOMContentLoaded', async function () {
        await initPageOfflineFeatures();

        if (navigator.onLine) {
            await syncPendingOutbox();
            await refreshSnapshotFromServer();
            await renderPendingBatchesOnListPage();
            warmAllOfflineCaches();
        }
    });
})();
