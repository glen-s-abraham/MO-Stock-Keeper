---
description: "Task list template for feature implementation"
---

# Tasks: FSSAI Compliant Batch Labels

**Input**: Design documents from `/specs/005-fssai-batch-labels/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify project compiles and application properties are ready for new settings.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T002 Add `registeredOfficeAddress` and `customerCareAddress` fields to `src/main/java/com/mushroom/stockkeeper/service/SettingsService.java`
- [x] T003 Ensure the `SettingsService` makes the new addresses available to the frontend globally or update `src/main/java/com/mushroom/stockkeeper/controller/HarvestBatchController.java` to inject them into the model.

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 3 - Database Seeding for Label Readiness (Priority: P2)

**Goal**: Deploy seed data containing sample farm details, FSSAI license numbers, and storage instructions so that the label printing feature can be tested and demonstrated immediately.
*(Note: Executing US3 first because it sets up the data needed for US1/US2 to render properly on the UI)*

**Independent Test**: Run the seeding script and verify that the database contains the required FSSAI and farm configuration data.

### Implementation for User Story 3

- [x] T004 [US3] Create or update `src/main/java/com/mushroom/stockkeeper/config/DataSeeder.java` to insert default values for `company.registered_office_address` and `company.customer_care_address` if they don't exist in `AppSetting`.
- [x] T005 [US3] Ensure `DataSeeder` gives sample products an `fssaiLicenseNumber` and enables `nutritionEnabled = true` with dummy `NutritionLineItem` data.
- [x] T013 [US3] Add 3-4 more dummy `NutritionLineItem`s to `src/main/java/com/mushroom/stockkeeper/config/DataInitializer.java` to meet the 5-6 items requirement.

**Checkpoint**: At this point, User Story 3 should be fully functional and testable independently

---

## Phase 4: User Story 1 - Print FSSAI Compliant Label (75x75mm or larger) (Priority: P1) 🎯 MVP

**Goal**: Print an FSSAI-compliant batch label of size 75x75mm (or larger) that automatically includes a nutrition table.

**Independent Test**: Can be fully tested by generating a print preview for a product with nutrition data on a 75x75 paper size and verifying all required FSSAI fields are present and aligned.

### Implementation for User Story 1

- [x] T006 [P] [US1] Update `src/main/resources/templates/batches/print.html` to inject and display the `registeredOfficeAddress` and `customerCareAddress`.
- [x] T007 [P] [US1] Update `src/main/resources/templates/batches/print.html` to reserve a 10mm x 10mm placeholder space for the veg symbol.
- [x] T008 [US1] Update `src/main/resources/templates/batches/print.html` to check if paper dimensions >= 75mm AND `batch.product.nutritionEnabled` is true. If yes, conditionally render the Nutrition Information table using Thymeleaf.
- [x] T009 [P] [US1] Update `src/main/resources/static/css/print.css` to add necessary styles for the nutrition table layout on print.
- [x] T014 [US1] Remove or hide the QR code from `src/main/resources/templates/batches/print.html`.
- [x] T015 [US1] Update `src/main/resources/templates/batches/print.html` to visually emphasize packing date, expiry date, storage instructions, and contact number.
- [x] T016 [US1] Update `src/main/resources/templates/batches/print.html` and `src/main/resources/static/css/print.css` to make the nutrition table auto-adjusting/scaling to fit the space.

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 5: User Story 2 - Print Standard FSSAI Label (under 75mm) (Priority: P2)

**Goal**: Print an FSSAI-compliant label that omits the nutrition table to save space on smaller formats.

**Independent Test**: Can be tested by generating a label on 50x50mm paper and verifying the nutrition table is absent but farm, storage, and date details remain well-aligned.

### Implementation for User Story 2

- [x] T010 [US2] Update `src/main/resources/templates/batches/print.html` logic to ensure that if `labelSheetSize` implies < 75mm dimensions, the nutrition table is fully hidden/omitted.

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T011 Update `src/test/java/com/mushroom/stockkeeper/controller/HarvestBatchControllerTest.java` to ensure new settings variables are asserted in the view model.
- [x] T012 Run manual validation using `specs/005-fssai-batch-labels/quickstart.md` steps.
- [x] T017 Run manual validation using `specs/005-fssai-batch-labels/quickstart.md` steps to verify the new emphasis and QR code removal layout changes.

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup completion
- **User Story 3**: Data Seeder setup, depends on Foundational.
- **User Story 1**: Label markup for large format, depends on Foundational (and US3 data is helpful).
- **User Story 2**: Label markup for small format, builds directly on US1 conditional logic.
- **Polish**: Depends on all stories.

### Parallel Opportunities
- Foundational and US3 could theoretically run in parallel if the Seeder uses raw queries, but standard flow is Sequential.
- T009 CSS styles and T006/T007 HTML layout could be assigned to different frontend developers in parallel.
