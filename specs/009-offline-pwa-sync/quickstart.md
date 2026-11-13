# Quickstart Validation Guide: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Feature**: `009-offline-pwa-sync`
**Date**: 2026-11-13

## Running Automated Verification

Run the unit and integration tests for Offline Snapshot & Outbox Sync:

```bash
mvn test -Dtest=OfflineSyncServiceTest,OfflineSyncControllerTest
```

Run the full test suite:

```bash
mvn test
```

## End-to-End Offline Validation Scenarios

1. **Snapshot Caching & Status Bar**:
   - Open `/dashboard`, `/batches/create`, `/batches/manual-labels`, `/procurement`, or `/admin/settings` while online.
   - Verify the navbar badge displays `Online (Synced)` and `GET /api/offline/snapshot` caches products, settings, batches, and procurement schedules in IndexedDB.
2. **Offline Batch Creation & Label Printing**:
   - Toggle DevTools Network to **Offline** (or disconnect Wi-Fi). Navbar badge switches to `Offline Mode`.
   - Submit **Create Batch** (`/batches/create`) or **Print New Manual Sticker Set** (`/batches/manual-labels`).
   - Verify the app queues the mutation in the local outbox, opens `/batches/offline-print`, renders all FSSAI stickers with nutrition facts and batch codes, and triggers `window.print()`.
3. **Offline Procurement Scheduler (ARPS) & Settings**:
   - While still Offline on `/procurement`, change a cycle's delivery date or create a schedule.
   - Verify the 12-month timeline recalculates locally in the browser and increments the pending sync counter (`Offline (2 pending)`).
4. **Automatic Reconnection Sync**:
   - Re-enable network connectivity (or click **Sync Now** in the navbar).
   - Verify queued mutations are sent to `POST /api/offline/sync`, persisted in the database with exact batch codes and schedule dates, and the badge returns to `Online (Synced)`.
