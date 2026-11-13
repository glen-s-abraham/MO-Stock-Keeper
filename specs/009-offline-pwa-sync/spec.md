# Feature Specification: Offline-First PWA & Outbox Sync (Batches, Labels, Settings & Procurement Scheduler)

**Feature Branch**: `[009-offline-pwa-sync]`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "make this website work offline atleast the batch related details cause the wifi cuts of at times and i am unable to use or print the details. so i need to ensure this app works offline too. when net available sync the data to the server else keep it offline the batches setting and the new procurement schedule should work offline. lets go with option a then which is recommended perform the whole speckit flow and complete the feature"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Offline Harvest Batch Creation & FSSAI / Manual Label Printing (Priority: P1)

As a farm operator working when Wi-Fi or internet drops, I want to open the Batches, New Batch, Manual Labels, and Batch Print views offline, create new harvest batches or manual label runs locally, and immediately print FSSAI-compliant stickers with valid QR codes (`window.print()`) so that packing operations never stop during an outage, and have those exact printed batch and unit codes automatically sync to the server when internet returns.

**Why this priority**: Solves the core operational blocker where intermittent Wi-Fi outages halt packing and sticker printing on the farm floor.

**Independent Test**: Can be tested by loading the application while online (caching the snapshot), switching the browser to offline mode (or intercepting/queuing offline mutations), creating a new batch or manual label print run, verifying the FSSAI print sheet renders locally with scannable QR codes and product/company details, and confirming that reconnecting syncs the exact batch and unit identifiers to the server database.

**Acceptance Scenarios**:

1. **Given** the user has opened the application at least once while online so product, setting, and batch data are cached locally, **When** Wi-Fi disconnects and the user navigates to Batches, Create Batch, or Manual Labels, **Then** the pages load from the offline cache without browser network error screens and display a clear Offline Mode indicator in the top navigation bar.
2. **Given** the browser is offline, **When** the user submits the Create Batch form for a product and quantity, **Then** the batch and its individual unit identifiers are generated locally, queued in the offline sync outbox, and rendered immediately on the offline FSSAI print sheet with scannable QR codes ready for `window.print()`.
3. **Given** the browser is offline, **When** the user generates a set of manual emergency stickers (`M-XXXX`), **Then** the next sequential manual codes are allocated from the locally cached sequence counter, rendered with write-in `Pkd:` and `Exp:` placeholders for printing, and queued for sequence synchronization.

---

### User Story 2 - Offline Procurement Scheduler (ARPS) & System Settings (Priority: P1)

As an operations manager working during a connectivity outage, I want to view the Adaptive Rolling Procurement Scheduler (ARPS) and System Settings offline, create procurement schedules, override cycle delivery dates (with the 12-month cascading ripple calculation, stockout risk warnings, and lead-time collision checks), and update settings/blackout dates locally so that all changes are preserved offline and synced when internet returns.

**Why this priority**: Ensures the new Procurement Scheduler and label/company settings remain 100% usable and editable during network drops.

**Independent Test**: Can be tested by overriding a cycle delivery date or creating a schedule while offline, verifying that the 12-month timeline updates locally with the shift toast notification and queues the action in the outbox, and confirming that reconnecting replays the schedule mutation to the server.

**Acceptance Scenarios**:

1. **Given** the browser is offline on the Procurement Scheduler view, **When** the user overrides a cycle's delivery date (or creates a new schedule or updates blackout dates), **Then** the 12-month cascading ripple recalculates immediately on the client, updates the local view and toast notification, and stores the mutation in the offline outbox queue.
2. **Given** the browser is offline on the System Settings view, **When** the user updates company details, label format dimensions, or holiday blackout dates, **Then** the settings are saved locally for immediate use by offline label printing and ARPS and queued in the offline outbox.

---

### User Story 3 - Automatic Reconnection Sync & Connection Status Bar (Priority: P1)

As a user moving between offline and online states, I want a live status indicator in the navigation bar showing whether the app is Online, Offline, or has Pending Sync items, and I want queued offline changes (batches, manual label sequences, procurement schedules/overrides, and settings) to sync automatically as soon as connectivity returns (or via a manual "Sync Now" button) without creating duplicate records.

**Why this priority**: Guarantees data integrity between offline floor operations and the central database once connectivity is restored.

**Independent Test**: Can be tested by queuing offline mutations (a batch creation, a manual label sequence update, a procurement schedule creation/override, and a settings update), restoring connectivity (or clicking "Sync Now"), and verifying `POST /api/offline/sync` processes all mutations idempotently and clears the pending outbox counter.

**Acceptance Scenarios**:

1. **Given** one or more mutations are queued in the offline outbox while disconnected, **When** network connectivity is restored (or the user triggers "Sync Now"), **Then** all queued mutations are sent to the server in chronological order, applied idempotently using unique mutation IDs, and the status badge updates to `Online (Synced)` with `0` pending items.
2. **Given** the same sync payload with an already-processed `clientMutationId` is sent twice due to a flaky connection, **When** the server receives the duplicate mutation ID, **Then** the server skips re-applying it and returns success so no duplicate batches or schedules are created.

---

### Edge Cases

- **Duplicate Sync Retries on Unstable Wi-Fi**: Each queued offline mutation MUST carry a unique `clientMutationId` persisted on the server so repeated sync attempts never create duplicate batches, inventory units, or procurement schedules.
- **Offline QR Code Integrity**: Inventory units created and printed while offline MUST send their exact client-generated UUIDs during sync so that physical QR labels already stuck on mushroom packs match the server's `InventoryUnit.uuid` records 1-to-1.
- **Manual Label Sequence Monotonicity**: When syncing offline manual label runs (`M-XXXX`), the server MUST advance `manual_label_last_sequence` to at least the highest sequence number printed offline (`Math.max(currentServerSeq, offlineEndSeq)`) so sequence numbers never regress.
- **External CDN Elimination**: All required scripts and stylesheets (including `Chart.js`) MUST be served locally from the application so pages never hang waiting for external internet CDNs when offline.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a Progressive Web App (PWA) configuration (`manifest.json`) and Service Worker (`sw.js`) that pre-caches static assets and caches application views (`/dashboard`, `/batches`, `/batches/create`, `/batches/manual-labels`, `/procurement`, `/admin/settings`) for offline navigation.
- **FR-002**: System MUST eliminate blocking external CDN script dependencies (such as external `Chart.js`) by serving them locally from the application's static assets.
- **FR-003**: System MUST provide a snapshot endpoint (`GET /api/offline/snapshot`) returning current active products (with nutrition items), application settings, recent harvest batches, manual label sequence state, and active procurement schedules (with their 12 cycle nodes) so the browser can keep its local IndexedDB cache updated whenever online.
- **FR-004**: System MUST display a persistent connection & sync status indicator in the top navigation bar showing current connectivity (`Online` vs `Offline Mode`), the count of pending offline mutations in the outbox, and a `Sync Now` action.
- **FR-005**: When offline, the system MUST intercept batch creation (`/batches/create`) and manual label generation (`/batches/manual-labels`), generate the batch and unit UUIDs (or manual `M-XXXX` codes) locally, store the operation in the IndexedDB outbox, and render an offline print view with client-generated QR codes and FSSAI layout supporting `window.print()`.
- **FR-006**: When offline, the system MUST intercept Procurement Scheduler actions (creating a schedule, overriding a cycle delivery date, and updating holiday blackout dates) and System Settings updates, apply the 12-month cascading ripple calculation and setting changes locally in IndexedDB, update the UI immediately, and queue the mutations in the IndexedDB outbox.
- **FR-007**: System MUST provide an idempotent synchronization endpoint (`POST /api/offline/sync`) that accepts a batch of queued offline mutations (`CREATE_BATCH`, `ALLOCATE_MANUAL_LABELS`, `CREATE_PROCUREMENT_SCHEDULE`, `OVERRIDE_PROCUREMENT_DATE`, `UPDATE_BLACKOUT_DATES`, `UPDATE_SETTINGS`), records processed `clientMutationId` values to prevent duplicate execution, persists all changes, and returns the updated snapshot state.
- **FR-008**: When `CREATE_BATCH` is replayed via `POST /api/offline/sync`, the server MUST persist the exact unit UUIDs generated and printed while offline so physical QR stickers maintain 100% traceability in live inventory.

### Key Entities *(include if feature involves data)*

- **OfflineSnapshotDto**: Read-only aggregate payload containing products, settings map, manual label sequence counter, recent harvest batches, and active procurement schedules with their 12 cycle nodes.
- **OfflineSyncRequest / OfflineMutationDto**: Represents an ordered list of offline operations queued by the client, each identified by a unique `clientMutationId`, `mutationType`, timestamp, and operation payload.
- **ProcessedOfflineMutation**: Server-side entity (`sk_processed_offline_mutations`) tracking applied `clientMutationId` values to guarantee idempotency across flaky network retries.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can navigate to Batches, Manual Labels, Procurement Scheduler, and Settings in under 2 seconds when Wi-Fi/network is completely disconnected.
- **SC-002**: 100% of batches created and printed offline preserve their exact printed QR code UUIDs when synced to the server upon reconnection.
- **SC-003**: 100% of queued offline mutations (batches, manual label sequences, procurement schedules/overrides, and settings) sync automatically within 10 seconds of network restoration (or immediately upon clicking "Sync Now") with zero duplicate records created on retry.
- **SC-004**: 100% of offline Procurement Scheduler delivery overrides compute identical 12-month cascading dates, stockout risk warnings, and holiday blackout shifts as the online engine.

## Assumptions

- Operators log in at least once while online on a device so the Service Worker and initial IndexedDB snapshot are installed in the browser.
- Local USB or OS-configured thermal/A4 printers remain accessible via the browser's native print dialog (`window.print()`) when Wi-Fi/internet is unavailable.
