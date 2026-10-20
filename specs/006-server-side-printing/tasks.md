---

description: "Task list for Remote Server-Side Label Printing implementation"
---

# Tasks: Remote Server-Side Label Printing

**Input**: Design documents from `/specs/006-server-side-printing/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/print-api.md, quickstart.md

**Tests**: Automated unit and integration tests are included per MO-Stock-Keeper Constitution Development & Quality Workflow (Section 1: Automated Verification).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project dependencies and print server default properties setup

- [X] T001 Add Apache PDFBox dependency (`org.apache.pdfbox:pdfbox:3.0.3`) in `pom.xml`
- [X] T002 [P] Configure default `app.print-server.url`, `app.print-server.connect-timeout-ms`, and `app.print-server.read-timeout-ms` properties in `src/main/resources/application.properties`, `src/main/resources/application-dev.properties`, and `src/main/resources/application-prod.properties`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core DTOs and `SettingsService` print server URL resolution that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create `PrintServerStatusResponse` DTO in `src/main/java/com/mushroom/stockkeeper/dto/PrintServerStatusResponse.java`
- [X] T004 [P] Create `ServerPrintResponse` DTO in `src/main/java/com/mushroom/stockkeeper/dto/ServerPrintResponse.java`
- [X] T005 Add `KEY_PRINT_SERVER_URL` (`"print_server_url"`) constant, `@Value("${app.print-server.url:http://192.168.1.53:8080}")` fallback, and `getPrintServerUrl()` method in `src/main/java/com/mushroom/stockkeeper/service/SettingsService.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Remote Server-Side Label Printing (Priority: P1) 🎯 MVP

**Goal**: Enable users to trigger remote server-side batch label printing from the batch print page by generating a multi-page PDF document replicating the current FSSAI label layout and dispatching it to the LAN print server (`H30PrintServer`), while retaining existing client-side browser printing.

**Independent Test**: Open `/batches/{id}/print` when the configured print server is reachable, click "Server Print", and verify a multi-page PDF matching the selected units and label dimensions is generated and POSTed to `/print` with visual confirmation shown to the user.

### Tests for User Story 1

- [X] T006 [P] [US1] Write unit tests for multi-page PDF label generation (75x75mm with nutrition, 50x50mm without nutrition, A4 grid, 1mm safe margin) and HTTP POST `/print` dispatch in `src/test/java/com/mushroom/stockkeeper/service/PrintServerServiceTest.java`
- [X] T007 [P] [US1] Write controller integration tests for `POST /batches/{id}/server-print` (full batch, `unitId`, and `seqStart`–`seqEnd` range) in `src/test/java/com/mushroom/stockkeeper/controller/HarvestBatchPrintControllerTest.java`

### Implementation for User Story 1

- [X] T008 [US1] Implement multi-page PDF batch label generation (replicating FSSAI layout, 1mm safe margin, 10mm Veg symbol placeholder, conditional nutrition table, and bounding-box scaling) and HTTP `POST /print` dispatch in `src/main/java/com/mushroom/stockkeeper/service/PrintServerService.java`
- [X] T009 [US1] Add `POST /batches/{id}/server-print` endpoint (supporting `unitId`, `seqStart`, `seqEnd` filtering) in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`
- [X] T010 [US1] Add the "Server Print" button, CSRF-aware AJAX trigger to `POST /batches/{id}/server-print`, in-progress spinner/disable lock, and success/error alert banner alongside the existing client-side "Print" button in `src/main/resources/templates/batches/print.html`

**Checkpoint**: User Story 1 is functional and independently testable

---

## Phase 4: User Story 2 - Dynamic Printer Connectivity Verification & Button State (Priority: P1)

**Goal**: Automatically check connectivity to the configured LAN print server with a strict timeout (`<= 1500ms`) and enable the "Server Print" button on `/batches/{id}/print` only when server-side printer connectivity is available.

**Independent Test**: Load `/batches/{id}/print` and query `GET /batches/print-server/status` both when the print server is online and when it is unreachable/offline; verify the "Server Print" button enables when online and disables with an unavailable status badge when offline, without delaying page load beyond 2 seconds.

### Tests for User Story 2

- [X] T011 [P] [US2] Add unit tests for print server connectivity probing (reachable mock server, unreachable port timeout `<= 1500ms`, and malformed URL handling) in `src/test/java/com/mushroom/stockkeeper/service/PrintServerServiceTest.java`
- [X] T012 [P] [US2] Add controller integration tests for `GET /batches/print-server/status` and `printServerAvailable` model attribute on `GET /batches/{id}/print` in `src/test/java/com/mushroom/stockkeeper/controller/HarvestBatchPrintControllerTest.java`

### Implementation for User Story 2

- [X] T013 [US2] Implement `checkConnectivity()` with strict `1500ms` socket/HTTP timeout and safe URI validation in `src/main/java/com/mushroom/stockkeeper/service/PrintServerService.java`
- [X] T014 [US2] Add `GET /batches/print-server/status` endpoint and populate initial `printServerAvailable` and `printServerUrl` model attributes in `GET /batches/{id}/print` in `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java`
- [X] T015 [US2] Update `src/main/resources/templates/batches/print.html` to bind `th:disabled="${!printServerAvailable}"`, display a connectivity status badge, and verify live status via `GET /batches/print-server/status` on page load

**Checkpoint**: User Stories 1 and 2 work together with live connectivity gating

---

## Phase 5: User Story 3 - Print Server Configuration Management (Priority: P2)

**Goal**: Allow administrators to view and update the LAN Print Server URL in System Settings (`/admin/settings`) with fallback to `application.properties` and strict `ROLE_ADMIN` authorization.

**Independent Test**: Log in as `ADMIN`, open `/admin/settings`, update `print_server_url`, save, and verify `SettingsService.getPrintServerUrl()` returns the updated URL and non-admin users are denied access.

### Tests for User Story 3

- [X] T016 [P] [US3] Add integration tests for `/admin/settings` print server configuration persistence and `@PreAuthorize("hasRole('ADMIN')")` role boundary enforcement in `src/test/java/com/mushroom/stockkeeper/controller/HarvestBatchPrintControllerTest.java`

### Implementation for User Story 3

- [X] T017 [US3] Enforce `@PreAuthorize("hasRole('ADMIN')")` and supply default `print_server_url` to the settings model in `src/main/java/com/mushroom/stockkeeper/controller/AdminController.java`
- [X] T018 [US3] Add the `Print Server URL` (`name="print_server_url"`) input field and helper text under the "Label Print Settings" section in `src/main/resources/templates/admin/settings.html`

**Checkpoint**: All user stories (US1, US2, US3) are independently functional and tested

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end validation, regression testing, and compliance verification

- [X] T019 Run full test suite (`./mvnw test`) and validate all quickstart scenarios from `specs/006-server-side-printing/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3 - P1)**: Depends on Foundational (Phase 2)
- **User Story 2 (Phase 4 - P1)**: Depends on Foundational (Phase 2) and integrates with `PrintServerService` / `HarvestBatchController` / `print.html` from US1
- **User Story 3 (Phase 5 - P2)**: Depends on Foundational (Phase 2) - Can run in parallel with US1/US2 since it modifies `AdminController.java` and `admin/settings.html`
- **Polish (Phase 6)**: Depends on Phases 3, 4, and 5 completion

### Parallel Opportunities

- `T003` (`PrintServerStatusResponse.java`) and `T004` (`ServerPrintResponse.java`) can be created in parallel.
- `T006` (`PrintServerServiceTest.java`) and `T007` (`HarvestBatchPrintControllerTest.java`) can be written in parallel.
- **User Story 3** (`T017` in `AdminController.java`, `T018` in `admin/settings.html`) touches separate files from **User Story 1 & 2** (`PrintServerService.java`, `HarvestBatchController.java`, `batches/print.html`) and can be implemented in parallel after Phase 2.

---

## Parallel Example: User Story 1 & 3

```bash
# Parallel DTO creation in Phase 2:
Task: "Create PrintServerStatusResponse DTO in src/main/java/com/mushroom/stockkeeper/dto/PrintServerStatusResponse.java"
Task: "Create ServerPrintResponse DTO in src/main/java/com/mushroom/stockkeeper/dto/ServerPrintResponse.java"

# Parallel Story execution after Phase 2:
Task: "Implement multi-page PDF batch label generation and HTTP POST /print dispatch in src/main/java/com/mushroom/stockkeeper/service/PrintServerService.java"
Task: "Enforce @PreAuthorize(\"hasRole('ADMIN')\") and supply default print_server_url in src/main/java/com/mushroom/stockkeeper/controller/AdminController.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Complete Phase 1: Setup (`pom.xml` & `application*.properties`)
2. Complete Phase 2: Foundational (DTOs & `SettingsService`)
3. Complete Phase 3 & Phase 4: Remote Server-Side Label Printing + Connectivity Gating (P1 stories)
4. **STOP and VALIDATE**: Verify multi-page PDF generation, connectivity gating, and `/batches/{id}/server-print`
5. Complete Phase 5: Admin Settings UI (`admin/settings.html` & `AdminController`)
6. Complete Phase 6: Full regression test execution (`./mvnw test`)
