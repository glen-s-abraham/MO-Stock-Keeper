# Phase 0 Research: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Feature**: `009-offline-pwa-sync`
**Date**: 2026-11-13

## 1. Service Worker & Caching Strategy

- **Decision**: Register `/sw.js` at the root scope (`/`) via `SecurityConfig` and `OfflinePwaController`, using a **Network-First with Cache Fallback** strategy for application HTML routes (`/dashboard`, `/batches`, `/batches/create`, `/batches/manual-labels`, `/batches/offline-print`, `/procurement`, `/admin/settings`) and a **Cache-First** strategy for static CSS, JS, fonts, and icons (`/css/**`, `/js/**`, `/favicon.svg`, `/manifest.json`).
- **Rationale**: Ensures users always see live server data when online, while transparently serving the cached page shell when Wi-Fi drops without browser "No Internet" error pages.
- **Alternatives considered**: Cache-First for HTML pages. Rejected because server-rendered Thymeleaf pages would display stale server HTML when online unless explicitly invalidated.

## 2. IndexedDB Store & Outbox Queue (`offline-sync.js`)

- **Decision**: Implement a self-contained browser offline engine (`/js/offline-sync.js`) backed by IndexedDB database `mo_stock_keeper_offline_v1` with object stores:
  - `snapshot` (keyed by `"current"`): Holds the latest `OfflineSnapshotDto` (`products`, `settings`, `manualLabelLastSequence`, `batches`, `procurementSchedules`).
  - `outbox` (auto-increment / keyed by `clientMutationId`): Ordered FIFO queue of offline mutations created while disconnected (`CREATE_BATCH`, `ALLOCATE_MANUAL_LABELS`, `SET_MANUAL_LABEL_SEQUENCE`, `CREATE_PROCUREMENT_SCHEDULE`, `OVERRIDE_PROCUREMENT_DATE`, `UPDATE_BLACKOUT_DATES`, `UPDATE_SETTINGS`).
  - `offline_print_jobs` (keyed by `"latest"`): Holds the most recently generated offline batch or manual label print job so `/batches/offline-print` can render and print it via `window.print()` with zero network connectivity.
- **Rationale**: IndexedDB is asynchronous, built into every modern desktop and mobile browser, persists across tab reloads and browser restarts, and is accessible from both standard page scripts and Service Workers.

## 3. Idempotent Server Synchronization & Batch Code Preservation

- **Decision**:
  1. Create a JPA entity `ProcessedOfflineMutation` (`sk_processed_offline_mutations`) storing `clientMutationId` (unique), `mutationType`, and `processedAt`.
  2. When a batch is created offline, the client generates a deterministic `batchCode` (`B-YYYYMMDD-XXXXXX`) and prints labels displaying that exact `batchCode`. When replayed via `POST /api/offline/sync`, `BatchService.createBatch(product, quantity, batchDate, batchCode)` persists that exact `batchCode` (and unit UUIDs `batchCode-1..N`) if not already present.
- **Rationale**: Preserves 1-to-1 traceability between labels printed during a Wi-Fi outage and database records synced later, and prevents duplicate batches if a flaky connection retries the sync POST.
