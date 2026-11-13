# Implementation Plan: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Branch**: `[009-offline-pwa-sync]` | **Date**: 2026-11-13 | **Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/009-offline-pwa-sync/spec.md)

**Input**: Feature specification from `/specs/009-offline-pwa-sync/spec.md`

## Summary

Implement Option A (**Progressive Web App + IndexedDB Outbox Sync**) so that **Harvest Batches**, **Manual & Batch Label Printing**, **System Settings**, and the **Adaptive Rolling Procurement Scheduler (ARPS)** work seamlessly during Wi-Fi/internet outages and automatically synchronize with the Spring Boot backend once connectivity returns.

## Technical Context

**Language/Version**: Java 21 LTS + Vanilla ES6 JavaScript (Service Worker + IndexedDB)

**Primary Dependencies**: Spring Boot 3.4.x, Spring Data JPA, Spring Security, Thymeleaf, Bootstrap 5.3

**Storage**: Server: H2 / PostgreSQL (`sk_processed_offline_mutations` + existing tables); Client: IndexedDB (`mo_stock_keeper_offline_v1`) + Cache Storage API (`mo-stock-pwa-v1`)

**Testing**: JUnit 5, Spring Boot Test (`MockMvc`), Mockito

**Target Platform**: Desktop & Mobile Web Browsers (Chrome, Edge, Firefox, Safari)

**Project Type**: Web Application (Spring Boot MVC + PWA Offline Layer)

**Performance Goals**: `< 100ms` local IndexedDB read/write when offline; `< 500ms` batch sync replay when back online

**Constraints**: Must preserve 1-to-1 `BatchCode` and `InventoryUnit.uuid` traceability between offline-printed stickers and synced database records, and enforce idempotent mutation replay via `clientMutationId`.

**Scale/Scope**: Offline caching for all products, settings, recent batches, manual label sequences, and 12-month procurement schedules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Financial Integrity & Explicit Auditability**: PASS — Offline sync logs every replayed mutation in `AuditService` and `ProcessedOfflineMutation`.
- **II. Complete Inventory Traceability & QR Lifecycle**: PASS — Offline batch creation generates deterministic `BatchCode` and `InventoryUnit` UUIDs (`B-YYYYMMDD-XXXXXX-1..N`) that are preserved verbatim when synced to the server.
- **III. Strict Security & Data Ownership**: PASS — `/api/offline/**` endpoints require authentication and CSRF protection; `manifest.json` and `sw.js` are served cleanly for PWA installation.
- **IV. Defensive Concurrency & Data Consistency**: PASS — `ProcessedOfflineMutation` enforces a unique constraint on `clientMutationId` to prevent duplicate replay under flaky networks.
- **V. Database-Driven Aggregation & Performance**: PASS — Snapshot queries fetch bounded recent batches and active schedules.
- **VI. Physical Printing & Hardware Constraints**: PASS — Offline label printing uses the exact same CSS sheet classes, 1mm safe margins, and `autoFitLabels()` binary-search bounding-box scaling as `batches/print.html`.

## Project Structure

### Documentation (this feature)

```text
specs/009-offline-pwa-sync/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── offline-sync-api.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/mushroom/stockkeeper/
├── config/
│   └── SecurityConfig.java
├── controller/
│   ├── OfflineSyncController.java
│   └── HarvestBatchController.java
├── dto/
│   ├── OfflineSnapshotDto.java
│   ├── OfflineMutationDto.java
│   ├── OfflineSyncRequest.java
│   └── OfflineSyncResponse.java
├── model/
│   └── ProcessedOfflineMutation.java
├── repository/
│   └── ProcessedOfflineMutationRepository.java
└── service/
    ├── BatchService.java
    └── OfflineSyncService.java

src/main/resources/
├── static/
│   ├── manifest.json
│   ├── sw.js
│   └── js/
│       └── offline-sync.js
└── templates/
    ├── layout/
    │   └── base.html
    └── batches/
        └── offline-print.html

src/test/java/com/mushroom/stockkeeper/
├── service/
│   └── OfflineSyncServiceTest.java
└── controller/
    └── OfflineSyncControllerTest.java
```

## Complexity Tracking

No constitution violations.
