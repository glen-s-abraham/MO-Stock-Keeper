# Tasks: Adaptive Rolling Procurement Scheduler (ARPS)

**Input**: Design documents from `/specs/008-adaptive-procurement-scheduler/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit and controller integration tests are included per MO-Stock-Keeper Constitution Development & Quality Workflow Principle 1.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify project template directory and navigation integration points

- [X] T001 Create procurement template directory at `src/main/resources/templates/procurement/`
- [X] T002 Add top-level `Procurement` navigation tab link (`@{/procurement}`) in `src/main/resources/templates/layout/base.html`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core JPA entities, repositories, and global blackout settings support that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Create `ProcurementSchedule` JPA entity (`sk_procurement_schedules`) with `@Version` optimistic locking in `src/main/java/com/mushroom/stockkeeper/model/ProcurementSchedule.java`
- [X] T004 [P] Create `ProcurementCycleNode` JPA entity (`sk_procurement_cycle_nodes`) with cycle dates, manual override flag, and risk flags in `src/main/java/com/mushroom/stockkeeper/model/ProcurementCycleNode.java`
- [X] T005 [P] Create `ProcurementScheduleRepository` interface in `src/main/java/com/mushroom/stockkeeper/repository/ProcurementScheduleRepository.java`
- [X] T006 [P] Create `ProcurementCycleNodeRepository` interface in `src/main/java/com/mushroom/stockkeeper/repository/ProcurementCycleNodeRepository.java`
- [X] T007 Add global `procurement_blackout_dates` setting key and parser/setter helpers in `src/main/java/com/mushroom/stockkeeper/service/SettingsService.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Create Procurement Schedule & View 12-Month Rolling Timeline (Priority: P1) 🎯 MVP

**Goal**: Allow users to navigate to the Procurement Scheduler tab, create a new schedule via a configuration modal (`Schedule_Name`, `Supply_Lasting_Days`, `Vendor_Lead_Time`, `Action_Buffer`, `Preferred_Delivery_Day`), and view the 12-month cascading projection (`[Action Date] -> [Cutoff Date] -> [Delivery Date]`) with backward snapping to the preferred delivery day.

**Independent Test**: Navigate to `/procurement`, create `"Raw Substrate - 175kg"` (`Supply_Lasting_Days=28`, `Vendor_Lead_Time=15`, `Action_Buffer=3`, `Preferred_Delivery_Day=6`), and verify 12 sequential cycles are created with Saturday delivery dates and accurate cutoff and action dates.

### Implementation for User Story 1

- [X] T008 [US1] Implement 12-month cascading projection algorithm (`createSchedule`, `snapBackwardToPreferredDay`, cutoff/action date computation) in `src/main/java/com/mushroom/stockkeeper/service/ProcurementSchedulerService.java`
- [X] T009 [US1] Implement `GET /procurement` and `POST /procurement/schedules` (plus soft-delete `POST /procurement/schedules/{id}/delete`) endpoints in `src/main/java/com/mushroom/stockkeeper/controller/ProcurementController.java`
- [X] T010 [US1] Build the New Schedule configuration modal and 12-Month Timeline View (`[Action Date] -> [Cutoff Date] -> [Delivery Date]`) in `src/main/resources/templates/procurement/index.html`

**Checkpoint**: User Story 1 is functional and independently testable

---

## Phase 4: User Story 2 - Manual Delivery Date Override & Cascading "Ripple" Recalculation (Priority: P1)

**Goal**: Enable inline "Edit Delivery Date" calendar picker overrides on any cycle node (`1..12`), enforce Lead Time Collision blocking, cascade recalculations across subsequent cycles through Month 12, flag Stockout Risk gaps (`> Supply_Lasting_Days`) in Orange, and display the toast notification `"Delivery shifted by X days. Subsequent 12 months successfully recalculated."`

**Independent Test**: Override Cycle 1's delivery date by +4 days on `/procurement` and verify Cycles 2–12 cascade, toast notification appears, lead-time violations (`< today + vendorLeadTime`) are blocked with exact error message, and gaps exceeding `Supply_Lasting_Days` flag the node in Orange with the stockout risk warning.

### Implementation for User Story 2

- [X] T011 [US2] Implement `overrideDeliveryDate` cascading ripple logic, Lead Time Collision validation, and Stockout Gap risk calculation in `src/main/java/com/mushroom/stockkeeper/service/ProcurementSchedulerService.java`
- [X] T012 [US2] Implement `POST /procurement/schedules/{scheduleId}/override` and `POST /procurement/api/schedules/{scheduleId}/override` endpoints in `src/main/java/com/mushroom/stockkeeper/controller/ProcurementController.java`
- [X] T013 [US2] Add inline "Edit Delivery Date" calendar pickers, Orange Stockout Risk warnings, Lead Time Collision error alerts, and Bootstrap Toast feedback loop in `src/main/resources/templates/procurement/index.html`

**Checkpoint**: User Stories 1 and 2 are functional and independently testable

---

## Phase 5: User Story 3 - Hub View Dashboard with Action Date Status Alerts (Priority: P2)

**Goal**: Provide a clean Hub View dashboard on `/procurement` displaying active schedules with `Action_Date` highlighted in Yellow (Upcoming) or Red (Overdue).

**Independent Test**: View active schedules on `/procurement` whose current cycle `Action_Date` is before today (Red / Overdue) vs within the upcoming window (Yellow / Upcoming).

### Implementation for User Story 3

- [X] T014 [US3] Implement Hub View status classification helper (`OVERDUE` in Red when `actionDate < today`, `UPCOMING` in Yellow when `actionDate` is within upcoming window / current month) in `src/main/java/com/mushroom/stockkeeper/service/ProcurementSchedulerService.java`
- [X] T015 [US3] Render the Hub View dashboard cards/table with Yellow (Upcoming) and Red (Overdue) `Action_Date` highlights in `src/main/resources/templates/procurement/index.html`

**Checkpoint**: User Stories 1, 2, and 3 are functional and independently testable

---

## Phase 6: User Story 4 - Global Holiday Blackout Date Adjustment (Priority: P2)

**Goal**: Allow configuring global `Blackout_Dates` (e.g., Christmas, Onam) so that any calculated `Action_Date` falling on a blackout date automatically shifts backward 1–2+ days before the holiday.

**Independent Test**: Configure a holiday date in `Blackout_Dates` that collides with a cycle's unadjusted `Action_Date`, recalculate/view the schedule, and verify `Action_Date` shifts backward prior to the holiday.

### Implementation for User Story 4

- [X] T016 [US4] Integrate Holiday Blackout backward adjustment and `recalculateAllActiveSchedules` into `src/main/java/com/mushroom/stockkeeper/service/ProcurementSchedulerService.java`
- [X] T017 [US4] Implement `POST /procurement/blackout-dates` endpoint in `src/main/java/com/mushroom/stockkeeper/controller/ProcurementController.java` and trigger schedule recalculation on settings update in `src/main/java/com/mushroom/stockkeeper/controller/AdminController.java`
- [X] T018 [P] [US4] Add Holiday Blackout Dates configuration modal/section in `src/main/resources/templates/procurement/index.html` and settings field in `src/main/resources/templates/admin/settings.html`

**Checkpoint**: All 4 user stories are complete and independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Automated unit and integration test suites and end-to-end validation

- [X] T019 [P] Create unit tests for 12-month projection, backward day snapping, ripple override, stockout gap detection, lead-time collision blocking, and holiday blackout adjustment in `src/test/java/com/mushroom/stockkeeper/service/ProcurementSchedulerServiceTest.java`
- [X] T020 [P] Create MockMvc controller integration tests covering Hub View, Timeline View, schedule creation, delivery override toast/errors, blackout dates, and authentication in `src/test/java/com/mushroom/stockkeeper/controller/ProcurementControllerTest.java`
- [X] T021 Run full Maven test suite (`mvn test`) to verify all new and existing tests pass cleanly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3–6)**: Depend on Foundational phase completion (`T003`–`T007`)
  - US1 (P1) → US2 (P1) → US3 (P2) → US4 (P2)
- **Polish & Tests (Phase 7)**: Depends on Phase 3–6 implementation completion

### Parallel Opportunities

- `T003`, `T004`, `T005`, `T006` in Phase 2 operate on independent files and can run in parallel.
- `T019` and `T020` in Phase 7 operate on independent test files and can run in parallel.

---

## Implementation Strategy

### MVP First (User Stories 1 & 2)

1. Complete Phase 1: Setup (`T001`–`T002`)
2. Complete Phase 2: Foundational (`T003`–`T007`)
3. Complete Phase 3 & Phase 4: User Stories 1 & 2 (`T008`–`T013`)
4. Complete Phase 5 & Phase 6: User Stories 3 & 4 (`T014`–`T018`)
5. Complete Phase 7: Automated Tests & Full Suite Validation (`T019`–`T021`)
