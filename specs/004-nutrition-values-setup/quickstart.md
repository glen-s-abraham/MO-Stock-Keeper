# Validation Guide: Nutrition Values Setup

## Prerequisites
- Local development environment with Java 21 and Maven.
- Application running locally (`mvn spring-boot:run`) connected to H2 memory DB.

## Scenario 1: Configure and Display Nutrition Values

**Objective**: Verify a product can have nutrition values configured, saved, and rendered correctly on the product view page.

### Steps
1. Navigate to the Products management section in the web application (e.g., `http://localhost:8080/products`).
2. Click "Add New Product" or edit an existing product.
3. Locate the "Nutrition Values as Line Items" toggle and enable it.
4. Set the "Base Unit Value" to `100` and select "g" for the unit.
5. In the Nutrition Table section, add a new row:
   - Component Name: `Calories`
   - Amount: `250`
   - Unit: `kcal`
6. Click "Add Row" to dynamically add another row without page reload.
7. Add a second row:
   - Component Name: `Protein`
   - Amount: `15`
   - Unit: `g`
8. Click "Save Product".

### Expected Outcomes
- The product saves successfully and the page redirects to the product list or view page.
- Opening the product details view shows a formatted nutrition table displaying the base unit (`100g`) and the two configured line items (`Calories: 250 kcal`, `Protein: 15 g`).
- Checking the H2 database console (`http://localhost:8080/h2-console`) shows the new rows in the `NUTRITION_LINE_ITEM` table linked to the correct `PRODUCT_ID`.

## Scenario 2: Validation of Base Unit

**Objective**: Verify that negative values for the base unit are rejected.

### Steps
1. Edit a product.
2. Enable "Nutrition Values as Line Items".
3. Set the "Base Unit Value" to `-50`.
4. Attempt to save the product.

### Expected Outcomes
- The save action is rejected.
- A validation error message is displayed next to the Base Unit Value field indicating it must be a positive number.

## Scenario 3: Print/Label Formatting Layout

**Objective**: Verify the digital view is properly formatted for physical printing per FR-006.

### Steps
1. View the saved product from Scenario 1 on the digital product page.
2. Use the browser's "Print Preview" function (Ctrl+P / Cmd+P).

### Expected Outcomes
- The nutrition table appears clearly in the print preview without being cut off.
- The table structure resembles a standard nutrition label layout, ready for label printing.
