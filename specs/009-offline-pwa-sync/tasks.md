# Tasks: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Input**: Design documents from `/specs/009-offline-pwa-sync/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit and controller integration tests are included per MO-Stock-Keeper Constitution Development & Quality Workflow Principle 1.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (PWA Shell & Static Asset Localization)

**Purpose**: Create Web App Manifest, Service Worker, and permit PWA static files in Spring Security

- [X] T001 [P] Create Web App Manifest in `src/main/resources/static/manifest.json`
- [X] T002 [P] Create Service Worker with Network-First HTML caching and Cache-First static asset caching in `src/main/resources/static/sw.js`
- [X] T003 Permit `/manifest.json` and `/sw.js` in `src/main/java/com/mushroom/stockkeeper/config/SecurityConfig.java`

---

## Phase 2: Foundational (Idempotency Store, Snapshot & Sync DTOs)

**Purpose**: Core server entities, repositories, and DTOs required for offline snapshot caching and outbox synchronization

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 [P] Create `ProcessedOfflineMutation` JPA entity (`sk_processed_offline_mutations`) in `src/main/java/com/mushroom/stockkeeper/model/ProcessedOfflineMutation.java`
- [X] T005 [P] Create `ProcessedOfflineMutationRepository` interface in `src/main/java/com/mushroom/stockkeeper/repository/ProcessedOfflineMutationRepository.java`
- [X] T006 [P] Create `OfflineSnapshotDto` in `src/main/java/com/mushroom/stockkeeper/dto/OfflineSnapshotDto.java`
- [X] T007 [P] Create `OfflineMutationDto`, `OfflineSyncRequest`, and `OfflineSyncResponse` in `src/main/java/com/mushroom/stockkeeper/dto/OfflineMutationDto.java`, `src/main/java/com/mushroom/stockkeeper/dto/OfflineSyncRequest.java`, and `src/main/java/com/mushroom/stockkeeper/dto/OfflineSyncResponse.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Offline Harvest Batch Creation & FSSAI / Manual Label Printing (Priority: P1) 🎯 MVP

**Goal**: Enable creating harvest batches and manual label runs when Wi-Fi/internet is disconnected, preserving exact batch/unit codes and rendering FSSAI labels locally for `window.print()`.

**Independent Test**: Disconnect network, submit Create Batch (`/batches/create`) or Manual Labels (`/batches/manual-labels`), verify labels render on `/batches/offline-print` with product/FSSAI/nutrition details and batch codes, and confirm replaying the outbox creates the batch with the exact `batchCode`.

### Implementation for User Story 1

- [X] T008 [US1] Add `createBatch(Product product, int quantity, LocalDate batchDate, String customBatchCode)` overload to preserve offline-generated batch codes in `src/main/java/com/mushroom/stockkeeper/service/BatchService.java`
- [X] T009 [US1] Add `GET /batches/offline-print` endpoint in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`
- [X] T010 [US1] Create client-rendered FSSAI offline label print template (`autoFitLabels()`, sheet sizing, nutrition table, manual & batch mode) in `src/main/resources/templates/batches/offline-print.html`

**Checkpoint**: User Story 1 server and print view components are ready

---

## Phase 4: User Story 2 - Offline Procurement Scheduler (ARPS) & System Settings (Priority: P1)

**Goal**: Support offline schedule creation, delivery date overrides (with 12-month cascading ripple math, stockout risk warnings, and holiday blackouts), and settings updates in IndexedDB.

**Independent Test**: Disconnect network on `/procurement` or `/admin/settings`, override a cycle delivery date or update settings, verify local UI updates immediately and queues the mutation in IndexedDB.

### Implementation for User Story 2

- [X] T011 [US2] Implement `OfflineSyncService` (`buildSnapshot()` and idempotent `processSync(OfflineSyncRequest)` handling `CREATE_BATCH`, `ALLOCATE_MANUAL_LABELS`, `SET_MANUAL_LABEL_SEQUENCE`, `CREATE_PROCUREMENT_SCHEDULE`, `OVERRIDE_PROCUREMENT_DATE`, `UPDATE_BLACKOUT_DATES`, `UPDATE_SETTINGS`) in `src/main/java/com/mushroom/stockkeeper/service/OfflineSyncService.java`
- [X] T012 [US2] Implement `OfflineSyncController` (`GET /api/offline/snapshot` and `POST /api/offline/sync`) in `src/main/java/com/mushroom/stockkeeper/controller/OfflineSyncController.java`

**Checkpoint**: User Story 2 backend sync and snapshot engine ready

---

## Phase 5: User Story 3 - Automatic Reconnection Sync & Connection Status Bar (Priority: P1)

**Goal**: Provide the IndexedDB offline engine (`offline-sync.js`), form interceptors for Batches, Manual Labels, Procurement Scheduler, and Settings when offline, automatic reconnection replay, and a live Online/Offline/Pending Sync status badge in the top navbar.

**Independent Test**: Queue offline mutations while disconnected, reconnect (or click `Sync Now`), and verify all queued mutations are replayed to `POST /api/offline/sync` and the navbar badge updates to `Online (Synced)`.

### Implementation for User Story 3

- [X] T013 [US3] Implement IndexedDB snapshot store, outbox queue, offline form interceptors (Batches, Manual Labels, ARPS ripple engine, Settings), and auto-sync engine in `src/main/resources/static/js/offline-sync.js`
- [X] T014 [US3] Add Online/Offline/Pending Sync status badge, `Sync Now` button, `manifest.json` link, and `offline-sync.js` registration in `src/main/resources/templates/layout/base.html`

**Checkpoint**: All 3 user stories are integrated and functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Automated unit and integration tests and full test suite verification

- [X] T015 [P] Create unit tests for `OfflineSyncService` (snapshot generation, offline batch creation with preserved `batchCode`, manual label sequence advancement, ARPS schedule creation/override sync, settings sync, and idempotent duplicate detection) in `src/test/java/com/mushroom/stockkeeper/service/OfflineSyncServiceTest.java`
- [X] T016 [P] Create MockMvc integration tests for `GET /api/offline/snapshot`, `POST /api/offline/sync`, and `GET /batches/offline-print` in `src/test/java/com/mushroom/stockkeeper/controller/OfflineSyncControllerTest.java`
- [X] T017 Run full Maven test suite (`mvn test`) to verify all new and existing tests pass cleanly

---

## Dependencies & Execution Order

- **Phase 1 (Setup)**: `T001`–`T003`
- **Phase 2 (Foundational)**: `T004`–`T007`
- **Phase 3–5 (User Stories 1–3)**: `T008`–`T014`
- **Phase 6 (Tests & Validation)**: `T015`–`T017`
