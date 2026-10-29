# Tasks: Manual Label Printer

**Input**: Design documents from `/specs/007-manual-label-printer/`

**Prerequisites**: [plan.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/plan.md), [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/spec.md), [research.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/research.md), [data-model.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/data-model.md), [contracts/manual-label-endpoints.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/contracts/manual-label-endpoints.md)

**Tests**: Included per MO-Stock-Keeper Constitution Development & Quality Workflow Principle 1 (unit and integration tests covering positive execution, edge cases, and concurrency/sequence integrity).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Exact file paths are included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create value object / DTO for manual sequence ranges and formatting helpers

- [X] T001 [P] Create `ManualLabelRange` DTO with `M-%04d` sequence formatting helper in `src/main/java/com/mushroom/stockkeeper/dto/ManualLabelRange.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core sequence persistence and atomic allocation in `SettingsService`

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Add `KEY_MANUAL_LABEL_LAST_SEQUENCE` (`"manual_label_last_sequence"`), `getManualLabelLastSequence()`, `setManualLabelLastSequence(int)`, and synchronized `@Transactional` `allocateManualLabelSequence(int count)` in `src/main/java/com/mushroom/stockkeeper/service/SettingsService.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Pre-Print Emergency Manual Labels with Remembered Sequence (Priority: P1) 🎯 MVP

**Goal**: Provide a dedicated Manual Label Printer section where users can select a product, enter a sticker count, atomically allocate the next sequential `M-XXXX` codes, and navigate to the print view without creating live stock records.

**Independent Test**: Open `/batches/manual-labels`, select a product, enter quantity `10`, submit, verify redirect to `/batches/manual-labels/print` with `M-0001`..`M-0010` and `manual_label_last_sequence` updated to `10` while `HarvestBatch` and `InventoryUnit` counts remain unchanged.

### Implementation for User Story 1

- [X] T003 [P] [US1] Create Manual Label Printer page template with next sequence display and generate form in `src/main/resources/templates/batches/manual-labels.html`
- [X] T004 [P] [US1] Add navigation links to `/batches/manual-labels` in `src/main/resources/templates/layout/base.html` and `src/main/resources/templates/batches/list.html`
- [X] T005 [US1] Implement `GET /batches/manual-labels` and `POST /batches/manual-labels/generate` endpoints in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`

**Checkpoint**: User Story 1 form and sequence allocation are functional

---

## Phase 4: User Story 2 - FSSAI Label Layout with Write-In Date Placeholders & Print Support (Priority: P1)

**Goal**: Render manual labels using the exact FSSAI layout, conditional nutrition table, and auto-scaling of online labels, displaying each sticker's `M-XXXX` sequence code as `Batch No:` and `____________` placeholders for `Pkd:` and `Exp:` across both browser printing and LAN Server Print.

**Independent Test**: Render `/batches/manual-labels/print?productId=1&seqStart=1&seqEnd=5` and execute `POST /batches/manual-labels/server-print?productId=1&seqStart=1&seqEnd=5`, verifying each label displays `M-0001`..`M-0005` and `____________` date placeholders in both HTML and PDF output.

### Implementation for User Story 2

- [X] T006 [P] [US2] Update `src/main/resources/templates/batches/print.html` to support per-unit `M-XXXX` `Batch No:`, `____________` write-in placeholders for `Pkd:` and `Exp:` when dates are null, and configurable `backUrl` and `serverPrintUrl`
- [X] T007 [P] [US2] Update `src/main/java/com/mushroom/stockkeeper/service/PrintServerService.java` (`buildStandalonePrintHtml` and `generateBatchLabelsPdfFallback` / `renderScaledLabelInBox` / `drawVirtualLabelContent`) to render per-unit `M-XXXX` batch codes and `____________` date placeholders when batch dates are null
- [X] T008 [US2] Implement `GET /batches/manual-labels/print` and `POST /batches/manual-labels/server-print` in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`

**Checkpoint**: Both client-side printing and remote LAN Server Print work end-to-end for manual labels

---

## Phase 5: User Story 3 - Reprint Specific Sequence Range or Adjust Sequence Counter (Priority: P2)

**Goal**: Allow operators to reprint a specific sequence range (`seqStart` to `seqEnd`) without advancing the stored sequence counter, or manually adjust/reset the remembered sequence counter.

**Independent Test**: Submit a reprint range (`seqStart=3`, `seqEnd=7`) and verify labels `M-0003`..`M-0007` render while `manual_label_last_sequence` remains unchanged; submit `POST /batches/manual-labels/sequence` with `lastSequence=25` and verify next code updates to `M-0026`.

### Implementation for User Story 3

- [X] T009 [P] [US3] Add Reprint Sequence Range form and Sequence Counter Adjustment form to `src/main/resources/templates/batches/manual-labels.html`
- [X] T010 [US3] Implement `POST /batches/manual-labels/sequence` with validation and audit logging in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`

**Checkpoint**: All user stories (US1, US2, US3) are independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns (Automated Verification)

**Purpose**: Comprehensive unit and controller tests verifying all requirements, edge cases, and zero phantom inventory side effects

- [X] T011 [P] Add unit tests for manual label PDF and HTML generation (per-unit `M-XXXX` codes and `____________` date placeholders) in `src/test/java/com/mushroom/stockkeeper/service/PrintServerServiceTest.java`
- [X] T012 [P] Create controller and sequence concurrency/persistence tests for manual label flows in `src/test/java/com/mushroom/stockkeeper/controller/ManualLabelPrintControllerTest.java`
- [X] T013 Execute full Maven test suite (`mvn test`) to validate all existing and new tests pass cleanly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on T001 (`ManualLabelRange`) - BLOCKS all user stories
- **User Stories (Phase 3–5)**: Depend on Foundational phase completion (T002)
- **Polish & Verification (Phase 6)**: Depends on Phases 3–5 completion

### Parallel Opportunities

- T003 and T004 can run in parallel
- T006 and T007 can run in parallel
- T011 and T012 can run in parallel

---

## Implementation Strategy

### MVP First (User Stories 1 & 2)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational sequence management)
2. Complete Phase 3 (US1) and Phase 4 (US2) for end-to-end manual sticker generation and printing
3. Complete Phase 5 (US3) for range reprinting and sequence counter adjustment
4. Complete Phase 6 (Automated Verification) and run full test suite
