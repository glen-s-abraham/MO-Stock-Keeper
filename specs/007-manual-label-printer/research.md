# Research & Technical Decisions: Manual Label Printer

## Decision 1: Sequence Storage & Atomic Allocation

- **Decision**: Persist the manual label sequence counter in `app_settings` under key `manual_label_last_sequence` (default `"0"`), managed via `synchronized` + `@Transactional` methods in `SettingsService`.
- **Rationale**:
  - `AppSetting` is already used across the application for persistent operational configuration without requiring schema migrations on production PostgreSQL or local H2 databases.
  - Using a synchronized transactional allocation method (`allocateManualLabelSequence(int count)`) guarantees that concurrent requests atomically advance the sequence and return a non-overlapping `[startSeq, endSeq]` range.
- **Alternatives Considered**:
  - Dedicated database table or native DB sequence: Rejected because native sequences cannot easily be adjusted/reset by an operator via UI when a printer jams, whereas an `AppSetting` row supports both atomic increment and operator reset.

## Decision 2: Post-Redirect-Get (PRG) Flow for Sequence Generation vs. Print View

- **Decision**: Separate sequence allocation (`POST /batches/manual-labels/generate`) from label rendering (`GET /batches/manual-labels/print?productId={id}&seqStart={start}&seqEnd={end}`).
- **Rationale**:
  - If `POST` rendered the print page directly, refreshing the browser (`F5`) or triggering `Server Print` would either prompt form resubmission or increment the sequence counter twice.
  - By redirecting `POST /batches/manual-labels/generate` to `GET /batches/manual-labels/print?productId=...&seqStart=...&seqEnd=...`, the print view is idempotent, bookmarkable, compatible with `window.location.search` forwarding in `triggerServerPrint()`, and directly reusable for the "Reprint Existing Sequence Range" workflow without advancing the counter.
- **Alternatives Considered**:
  - Direct rendering from `POST`: Rejected due to accidental double-increment risk on page reload or remote print calls.

## Decision 3: Template & PDF Rendering Reuse (`batches/print.html` & `PrintServerService`)

- **Decision**: Construct a transient `HarvestBatch` (with `batchDate = null`, `expiryDate = null`) and transient `InventoryUnit` items (with `uuid = String.format("M-%04d", seq)`) and reuse `templates/batches/print.html` and `PrintServerService`.
- **Rationale**:
  - Reusing `templates/batches/print.html` guarantees 100% visual parity with online stickers (including 10mm vegetarian symbol box, company details, net weight, MRP, conditional nutrition table for `>= 75mm`, dashed storage instructions box, and `autoFitLabels()` JS).
  - In `batches/print.html` and `PrintServerService`:
    - `Batch No:` displays `u.uuid` when `manualMode` is active (or when `u.uuid` starts with `"M-"`), so each sticker shows its own sequential code (`M-0001`, `M-0002`, ...).
    - `Pkd:` and `Exp:` fall back to `"____________"` when `batch.batchDate` / `batch.expiryDate` are `null`.
  - Both Headless Chrome / PrintServer `/render-pdf` and PDFBox fallback (`generateBatchLabelsPdfFallback`) render the per-unit `M-XXXX` code and `"____________"` date placeholders.
- **Alternatives Considered**:
  - Duplicating `batches/print.html` into a separate `manual-print.html`: Rejected because maintaining two copies of the FSSAI layout and auto-scaling script would introduce layout drift.
