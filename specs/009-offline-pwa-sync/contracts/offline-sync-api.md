# Phase 1 Interface Contracts: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Feature**: `009-offline-pwa-sync`
**Date**: 2026-11-13

## Endpoints

### 1. `GET /manifest.json` & `GET /sw.js`
- Serves the Web App Manifest and root-scoped Service Worker (`Service-Worker-Allowed: /`).

### 2. `GET /batches/offline-print`
- Pre-cached Thymeleaf print template that reads the active offline batch or manual label print job from IndexedDB (`offline_print_jobs` / `localStorage`), applies the configured `label_sheet_size` (`A4_40`, `A4_24`, `THERMAL_4x6`, `THERMAL_2x1`, `THERMAL_CUSTOM`), renders all FSSAI sticker blocks and nutrition tables, executes `autoFitLabels()`, and enables direct printing via `window.print()`.

### 3. `GET /api/offline/snapshot`
- Returns `200 OK` JSON (`OfflineSnapshotDto`) with products, settings, manual label sequence counter, recent harvest batches, and active procurement schedules.

### 4. `POST /api/offline/sync`
- **Request Body (`OfflineSyncRequest` JSON)**:
  ```json
  {
    "mutations": [
      {
        "clientMutationId": "mut-1731500000-abc1",
        "mutationType": "CREATE_BATCH",
        "payload": {
          "productId": "1",
          "quantity": "25",
          "batchDate": "2026-11-13",
          "batchCode": "B-20261113-482910"
        }
      }
    ]
  }
  ```
- **Response (`OfflineSyncResponse` JSON)**:
  ```json
  {
    "success": true,
    "appliedCount": 1,
    "skippedDuplicateCount": 0,
    "message": "Synced 1 offline change(s) successfully.",
    "snapshot": { ... }
  }
  ```
