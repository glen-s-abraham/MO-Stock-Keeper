# Implementation Tasks: default-monthly-snapshot

**Feature**: Default Monthly Snapshot
**Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/002-default-monthly-snapshot/spec.md)
**Plan**: [plan.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/002-default-monthly-snapshot/plan.md)

## Strategy

1. MVP: Update the default routing parameter in the Controller.
2. Increment: Add automated tests to ensure default behavior holds.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure.

- [X] T001 Verify project compiles and tests pass before starting.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*No foundational infrastructure changes required for this UI routing enhancement.*

---

## Phase 3: User Story 1 - Auto-load Current Month Snapshot (Priority: P1) 🎯 MVP

**Goal**: As a farm manager, I want the Harvest Dashboard to automatically load and display the Monthly Snapshot tab for the current month when I first navigate to the page, so that I don't have to manually click the tab and apply filters to see the most relevant immediate data.

**Independent Test**: Load `/dashboard/harvest` without parameters and verify the monthly tab is active and data is loaded.

### Implementation for User Story 1

- [X] T002 [US1] Change `defaultValue` of the `tab` `@RequestParam` from `"lifetime"` to `"monthly"` in `src/main/java/com/mushroom/stockkeeper/controller/DashboardController.java`
- [X] T003 [P] [US1] Add a test method to `src/test/java/com/mushroom/stockkeeper/controller/DashboardControllerTest.java` that performs a GET request to `/dashboard/harvest` without any parameters and asserts that `activeTab` is `"monthly"` and the `snapshotTotals` model attribute exists.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T004 Run manual validation according to `specs/002-default-monthly-snapshot/quickstart.md` to ensure the UI behaves perfectly under all scenarios.

## Dependencies

- User Story 1 (Auto-load Current Month Snapshot) is entirely independent and self-contained.

## Parallel Execution

- T003 (writing the test) can be done in parallel or TDD-style prior to T002.
