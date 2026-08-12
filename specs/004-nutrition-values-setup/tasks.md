---
description: "Task list for Nutrition Values Setup feature implementation"
---

# Tasks: Nutrition Values Setup

**Input**: Design documents from `specs/004-nutrition-values-setup/`

**Prerequisites**: plan.md, spec.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

*(No general project setup needed; feature builds on existing application).*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T001 Create database schema migration (e.g., Flyway/Liquibase) to add nutrition fields to `Product` table and create `NutritionLineItem` table in `src/main/resources/db/migration/`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Configure Product Nutrition Settings (Priority: P1) 🎯 MVP

**Goal**: Enable nutrition values as line items and configure a base unit value for a product.

**Independent Test**: Can be fully tested by navigating to a product's settings, enabling the nutrition feature, setting the base value, and saving the product.

### Implementation for User Story 1

- [X] T002 [P] [US1] Update `Product` entity with `hasNutritionValues`, `nutritionBaseUnitValue`, and `nutritionBaseUnitType` in `src/main/java/com/mushroom/stockkeeper/domain/Product.java`
- [X] T003 [US1] Update `ProductForm` / DTOs to include the new fields in `src/main/java/com/mushroom/stockkeeper/web/dto/` (or equivalent package)
- [X] T004 [US1] Update product edit template to include the toggle and base unit inputs in `src/main/resources/templates/product/edit.html` (or equivalent product form template)
- [X] T005 [US1] Implement validation for positive base unit value in the controller or form validator in `src/main/java/com/mushroom/stockkeeper/web/controller/`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - Populate Nutrition Table (Priority: P2)

**Goal**: Populate a tabular list of nutritional components for a product.

**Independent Test**: Can be fully tested by adding, editing, and removing rows in the nutrition table for a product and verifying they are saved and displayed.

### Implementation for User Story 2

- [X] T006 [P] [US2] Create `NutritionLineItem` entity with optimistic locking (`@Version`) in `src/main/java/com/mushroom/stockkeeper/domain/NutritionLineItem.java`
- [X] T007 [US2] Update `Product` entity with `@OneToMany` mapping to `NutritionLineItem` in `src/main/java/com/mushroom/stockkeeper/domain/Product.java`
- [X] T008 [US2] Update product form DTOs to support a list of `NutritionLineItem`s for form binding
- [X] T009 [US2] Update product edit template to include dynamic tabular setup in `src/main/resources/templates/product/edit.html`
- [X] T010 [P] [US2] Create vanilla JS script to handle dynamic row addition/removal in `src/main/resources/static/js/nutrition-table.js`
- [X] T011 [US2] Update product details view template to display the nutrition tabular data in `src/main/resources/templates/product/view.html`
- [X] T012 [P] [US2] Add print-specific CSS for the nutrition label format in `src/main/resources/static/css/print.css`

**Checkpoint**: User Stories 1 AND 2 should both work independently.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T013 Run scenarios from `quickstart.md` manually to validate end-to-end functionality

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: N/A
- **Foundational (Phase 2)**: Can start immediately.
- **User Stories (Phase 3+)**: Depend on Foundational phase completion. US1 must be completed before or alongside US2, as US2 relies on the base feature toggle introduced in US1.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Depends on T001 (Schema migration).
- **User Story 2 (P2)**: Integrates with US1 (Base Product fields) but tasks can be worked on largely in parallel once the entities are defined.

### Parallel Opportunities

- Entities (T002, T006) can be created in parallel.
- Static assets (T010 JS, T012 CSS) can be worked on in parallel with backend logic.

---

## Parallel Example: User Story 2

```bash
# Developer A focuses on the backend domain mapping:
Task: "Create NutritionLineItem entity with optimistic locking..."
Task: "Update Product entity with @OneToMany mapping..."

# Developer B focuses on the frontend interactivity:
Task: "Create vanilla JS script to handle dynamic row addition/removal..."
Task: "Add print-specific CSS for the nutrition label format..."
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2 (DB Schema).
2. Complete Phase 3 (US1).
3. Validate that a product can be configured to use nutrition data.

### Incremental Delivery

1. Deliver US1 (Configuration fields).
2. Deliver US2 (Tabular items & rendering).
3. End-to-end validation.
