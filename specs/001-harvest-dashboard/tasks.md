---
description: "Task list for Harvest Dashboard implementation"
---

# Tasks: harvest-dashboard

**Input**: Design documents from `/specs/001-harvest-dashboard/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Test tasks are not explicitly requested per the feature spec, but basic controller and service validation tasks are included as standard Spring Boot practice where appropriate.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure. As this integrates into an existing Spring Boot monolith, this phase mostly sets up the feature's package space if it didn't exist.

- [X] T001 Create package structure for dashboard (controller, service, dto) in `src/main/java/` as needed per implementation plan.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Create `ProductHarvestAggregateDto` in `src/main/java/mo/stock/keeper/dto/ProductHarvestAggregateDto.java`
- [X] T003 Create `DashboardService` interface (or base class) in `src/main/java/mo/stock/keeper/services/DashboardService.java`
- [X] T004 Create `DashboardController` in `src/main/java/mo/stock/keeper/controllers/DashboardController.java` with `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` and basic `/dashboard/harvest` GET mapping returning an empty Thymeleaf template.
- [X] T005 Create base empty Thymeleaf template in `src/main/resources/templates/dashboard/harvest.html`

**Checkpoint**: Foundation ready - basic empty page loads securely. User story implementation can now begin.

---

## Phase 3: User Story 1 - Lifetime Harvest Overview (Priority: P1) 🎯 MVP

**Goal**: As a farm manager, I want to see the total lifetime harvest for each available product since the commencement of the system.

**Independent Test**: Can be fully tested by loading the dashboard and verifying the displayed lifetime totals for each product against known historical harvest records.

### Implementation for User Story 1

- [X] T006 [P] [US1] Add JPQL native `@Query` for lifetime harvest totals grouping by product to `InventoryUnitRepository` in `src/main/java/mo/stock/keeper/repositories/InventoryUnitRepository.java`
- [X] T007 [US1] Implement `getLifetimeHarvestTotals()` method in `DashboardService` that calls the repository query
- [X] T008 [US1] Update `DashboardController` to inject lifetime totals into the model under `lifetimeTotals` attribute
- [X] T009 [US1] Update Thymeleaf template `src/main/resources/templates/dashboard/harvest.html` to render the "Lifetime Harvest" section using the `lifetimeTotals` data

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently. The dashboard shows lifetime stats.

---

## Phase 4: User Story 2 - Monthly Harvest Snapshot (Priority: P2)

**Goal**: As a farm manager, I want to view a monthly snapshot showing the total harvest of each product for a specific month and year.

**Independent Test**: Can be fully tested by selecting different month/year combinations and verifying the displayed totals match.

### Implementation for User Story 2

- [X] T010 [P] [US2] Add JPQL native `@Query` for date-range bounded harvest totals to `InventoryUnitRepository` in `src/main/java/mo/stock/keeper/repositories/InventoryUnitRepository.java` (can be shared with US3 if just using `startDate` and `endDate`)
- [X] T011 [US2] Implement `getMonthlyHarvestTotals(int year, int month)` method in `DashboardService`
- [X] T012 [US2] Update `DashboardController` to handle `?tab=monthly&month={m}&year={y}` query parameters and inject `snapshotTotals` into the model
- [X] T013 [US2] Update Thymeleaf template `src/main/resources/templates/dashboard/harvest.html` to include the Monthly tab UI, month/year dropdowns, and render the snapshot data if the monthly tab is active.

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently. Users can see lifetime and monthly snapshot stats.

---

## Phase 5: User Story 3 - Yearly Harvest Snapshot (Priority: P3)

**Goal**: As a farm manager, I want to view a yearly snapshot showing the total harvest of each product for a specific year.

**Independent Test**: Can be fully tested by selecting different years and verifying the totals match the annual aggregates.

### Implementation for User Story 3

- [X] T014 [US3] Implement `getYearlyHarvestTotals(int year)` method in `DashboardService` (using the bounded query added in US2)
- [X] T015 [US3] Update `DashboardController` to handle `?tab=yearly&year={y}` query parameters and route correctly to the service logic
- [X] T016 [US3] Update Thymeleaf template `src/main/resources/templates/dashboard/harvest.html` to include the Yearly tab UI, year dropdown, and render the snapshot data if the yearly tab is active.

**Checkpoint**: All user stories should now be independently functional. The dashboard supports lifetime, monthly, and yearly views.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T017 [P] Update existing controller/service unit tests in `src/test/java/mo/stock/keeper/controllers/DashboardControllerTest.java` to verify the dashboard model populates correctly for all tabs.
- [X] T018 Confirm that the Month/Year dropdown options are dynamically populated with valid years from the database by implementing `getAvailableHarvestYears()` in `DashboardService` and exposing it to the view.
- [X] T019 Run quickstart.md validation manually to ensure end-to-end flow meets all acceptance criteria.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1 (Phase 3) must be done. 
  - US2 (Phase 4) and US3 (Phase 5) can theoretically proceed in parallel, but sequentially (US1 → US2 → US3) makes sense for UI evolution in the single Thymeleaf template.
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2)
- **User Story 2 (P2)**: Integrates into the same UI as US1. 
- **User Story 3 (P3)**: Depends on US2's repository method implementation.

### Parallel Opportunities

- T002 (DTO) can run in parallel with basic controller/service setup.
- T006 (JPQL for lifetime) and T010 (JPQL for periodic) could run in parallel in the repository if working in a team.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently. It provides the core dashboard page and the most critical business view.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
