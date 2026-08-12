# Feature Specification: Nutrition Values Setup

**Feature Branch**: `[004-nutrition-values-setup]`

**Created**: 2026-08-12

**Status**: Draft

**Input**: User description: "so the products sectioni would like to add an additional setting to mark the nutrition values as line items.for that i need a parameter Unit Value Per 100g(configurable). and then a populatable tabular setup"

## Clarifications

### Session 2026-08-12
- Q: How should the nutrition table rows be populated initially? → A: Start completely blank, user adds all rows manually
- Q: Does the "Unit Value Per 100g" parameter need to support volume? → A: Yes, support both weight (g) and volume (ml)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Product Nutrition Settings (Priority: P1)

As a product manager, I want to enable nutrition values as line items and configure a base unit value for a product, so that I can provide detailed nutritional information.

**Why this priority**: It establishes the foundation for managing nutritional data on products.

**Independent Test**: Can be fully tested by navigating to a product's settings, enabling the nutrition feature, setting the base value, and saving the product.

**Acceptance Scenarios**:

1. **Given** a product editing view, **When** I toggle "Nutrition Values as Line Items", **Then** the nutrition settings section becomes active.
2. **Given** active nutrition settings, **When** I input a value into "Base Unit Value", select the unit, and save, **Then** the configuration is persisted to the product.

---

### User Story 2 - Populate Nutrition Table (Priority: P2)

As a product manager, I want to populate a tabular list of nutritional components (e.g., Calories, Protein, Fat) for a product, so that consumers can see the breakdown.

**Why this priority**: It provides the actual nutritional data points that will be displayed or calculated based on the base unit.

**Independent Test**: Can be fully tested by adding, editing, and removing rows in the nutrition table for a product.

**Acceptance Scenarios**:

1. **Given** the nutrition settings are enabled, **When** I add a new row to the nutrition table with a name and value, **Then** the row is added to the list.
2. **Given** a populated nutrition table, **When** I save the product, **Then** the tabular data is persisted and associated with the product.

### Edge Cases

- What happens when a user disables "Nutrition Values as Line Items" after having populated the tabular setup?
- How does the system handle negative values or non-numeric input for "Base Unit Value"?
- What happens if duplicate nutrition component names are entered in the table?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to toggle a setting "Nutrition Values as Line Items" on a product.
- **FR-002**: System MUST allow users to configure a numeric "Base Unit Value" (e.g., 100) and select its measurement unit (e.g., g, ml) for a product when nutrition values are enabled.
- **FR-003**: System MUST provide a tabular interface to manually add, edit, and remove individual nutritional components (e.g., Component Name, Amount, Unit) for a product, starting from a blank state.
- **FR-004**: System MUST persist the nutrition configuration and the tabular data associated with the product.
- **FR-005**: System MUST validate that the "Base Unit Value" is a positive number.
- **FR-006**: System MUST ensure that the nutrition tabular data is formatted for both digital display on the web application and for physical printing on labels/invoices.

### Key Entities *(include if feature involves data)*

- **Product / Product Settings**: Extended to include boolean flag for nutrition line items and the base unit value.
- **Nutrition Line Item**: Represents a single row in the nutrition table (e.g., Component Name, Value, Measurement Unit) linked to a Product.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can enable and configure nutrition settings for a product in under 1 minute.
- **SC-002**: Users can add 5 nutrition line items to the tabular setup without page reloads.
- **SC-003**: 100% of saved nutrition data is accurately persisted and retrieved on subsequent product edits.

## Assumptions

- Assumes that the configured "Base Unit Value" (e.g., 100g or 100ml) applies universally to all nutritional components in the tabular setup as a baseline.
- Assumes the tabular setup requires at least a Name (e.g., "Carbs") and a Value for each line item.
- Assumes data is retained but hidden if the "Nutrition Values as Line Items" setting is turned off.
