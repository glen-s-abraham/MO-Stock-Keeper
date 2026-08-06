# Implementation Tasks: snapshot-bargraphs

**Feature**: Snapshot Bargraphs
**Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/003-snapshot-bargraphs/spec.md)
**Plan**: [plan.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/003-snapshot-bargraphs/plan.md)

## Strategy

1. MVP: Implement the DTOs, endpoint, and the Monthly Snapshot (Daily/Weekly) chart.
2. Increment: Add the Yearly Snapshot (Monthly distribution) chart since it reuses the same endpoint structure and frontend logic.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure.

- [X] T001 Include Chart.js via CDN in `src/main/resources/templates/layout/base.html` (or `harvest.html` if page-specific blocks are used).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T002 [P] Create `ChartDatasetDto` in `src/main/java/com/mushroom/stockkeeper/dto/ChartDatasetDto.java`.
- [X] T003 [P] Create `ChartResponseDto` in `src/main/java/com/mushroom/stockkeeper/dto/ChartResponseDto.java`.
- [X] T004 [P] Create `TimeSeriesAggregateDto` in `src/main/java/com/mushroom/stockkeeper/dto/TimeSeriesAggregateDto.java`.

**Checkpoint**: Core DTOs for data transfer are available for all stories.

---

## Phase 3: User Story 1 - Monthly Snapshot Daily/Weekly Bargraph (Priority: P1) 🎯 MVP

**Goal**: As a farm manager, I want to view a bar graph in the Monthly Snapshot tab that visualizes harvest totals distributed either by day or by week.

**Independent Test**: Load `/dashboard/harvest?tab=monthly&month=8&year=2026`, verify the graph renders. Toggle to 'Weekly', verify the graph updates without full reload.

### Implementation for User Story 1

- [X] T005 [US1] Add JPQL queries using `EXTRACT()` to `src/main/java/com/mushroom/stockkeeper/repository/InventoryUnitRepository.java` to aggregate harvest totals by day for a given month/year, returning `TimeSeriesAggregateDto`.
- [X] T006 [US1] Add JPQL queries using `EXTRACT()` to `src/main/java/com/mushroom/stockkeeper/repository/InventoryUnitRepository.java` to aggregate harvest totals by week for a given month/year, returning `TimeSeriesAggregateDto`.
- [X] T007 [US1] Implement service methods in `src/main/java/com/mushroom/stockkeeper/service/DashboardService.java` to execute the daily/weekly queries and format the results into a `ChartResponseDto` (assigning distinct colors per product).
- [X] T008 [US1] Implement a new `@ResponseBody` `@GetMapping("/dashboard/harvest/chart-data")` in `src/main/java/com/mushroom/stockkeeper/controller/DashboardController.java` to serve the chart data.
- [X] T009 [US1] Add `<canvas>` and toggle buttons for Daily/Weekly to the Monthly tab section in `src/main/resources/templates/dashboard/harvest.html`.
- [X] T010 [US1] Write Javascript in `src/main/resources/templates/dashboard/harvest.html` to fetch data from `/dashboard/harvest/chart-data` and initialize/update the Chart.js instance.

---

## Phase 4: User Story 2 - Yearly Snapshot Monthly Bargraph (Priority: P2)

**Goal**: As a farm manager, I want to view a bar graph in the Yearly Snapshot tab that visualizes harvest totals distributed by month.

**Independent Test**: Load `/dashboard/harvest?tab=yearly&year=2026`, verify the graph renders with 12 months on the X-axis.

### Implementation for User Story 2

- [X] T011 [US2] Add JPQL query using `EXTRACT()` to `src/main/java/com/mushroom/stockkeeper/repository/InventoryUnitRepository.java` to aggregate harvest totals by month for a given year, returning `TimeSeriesAggregateDto`.
- [X] T012 [US2] Implement service method in `src/main/java/com/mushroom/stockkeeper/service/DashboardService.java` to execute the monthly query and format the results into a `ChartResponseDto`.
- [X] T013 [US2] Update the `/dashboard/harvest/chart-data` endpoint in `src/main/java/com/mushroom/stockkeeper/controller/DashboardController.java` to accept and handle `distribution=monthly`.
- [X] T014 [US2] Add `<canvas>` to the Yearly tab section in `src/main/resources/templates/dashboard/harvest.html`.
- [X] T015 [US2] Update the Javascript in `src/main/resources/templates/dashboard/harvest.html` to render the yearly chart when the yearly tab is active.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T016 [P] Write unit tests in `src/test/java/com/mushroom/stockkeeper/controller/DashboardControllerTest.java` to verify the new `/dashboard/harvest/chart-data` endpoint returns 200 OK and valid JSON format for all distribution types.
- [X] T017 Run manual validation per `specs/003-snapshot-bargraphs/quickstart.md`.

## Dependencies

- Phase 2 (Foundational) must complete before Phase 3 (US1).
- Phase 3 (US1) should complete before Phase 4 (US2) as it establishes the REST endpoint and core frontend logic.

## Parallel Execution

- T002, T003, T004 (DTO creation) can be implemented simultaneously.
- T016 (Endpoint tests) can be written alongside T008/T013 in a TDD workflow.
