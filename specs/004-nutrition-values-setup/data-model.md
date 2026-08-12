# Data Model: Nutrition Values Setup

## Entities

### `Product` (Modified)
The existing Product entity needs to be extended to support the new nutrition configuration.

**Added Fields:**
- `hasNutritionValues` (Boolean): Flag to indicate if this product uses nutrition line items. Default: `false`.
- `nutritionBaseUnitValue` (BigDecimal): The numeric value for the base unit (e.g., 100).
- `nutritionBaseUnitType` (String/Enum): The measurement unit for the base value (e.g., "g", "ml").

**Relationships:**
- `OneToMany` with `NutritionLineItem` (cascade = ALL, orphanRemoval = true)

### `NutritionLineItem` (New)
Represents a single row in the nutrition tabular setup for a product.

**Fields:**
- `id` (Long): Primary key, auto-generated.
- `version` (Long): Optimistic locking version (per Constitution IV).
- `componentName` (String): The name of the nutritional component (e.g., "Calories", "Protein"). Cannot be null/empty.
- `amount` (BigDecimal): The numeric amount of the component per base unit. Cannot be null.
- `measurementUnit` (String): The unit of measurement for this specific component (e.g., "kcal", "g", "mg").
- `displayOrder` (Integer): The order in which this row should be displayed in the table.
- `isHidden` (Boolean): Soft deletion flag (per Constitution I).

**Relationships:**
- `ManyToOne` with `Product`: The product this nutrition line belongs to.

## Validation Rules
- `nutritionBaseUnitValue` must be strictly greater than 0 if `hasNutritionValues` is true.
- `nutritionBaseUnitType` must not be null/empty if `hasNutritionValues` is true.
- `NutritionLineItem.componentName` must be unique per `Product` (optional constraint, but good practice to prevent duplicates).
- `NutritionLineItem.amount` must be greater than or equal to 0.

## State Transitions
- No complex state transitions. Entities are purely configuration data. Soft deletion applies when removing rows.
