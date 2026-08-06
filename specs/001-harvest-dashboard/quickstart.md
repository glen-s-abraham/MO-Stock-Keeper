# Quickstart & Validation Guide: Harvest Dashboard

This guide provides the steps to validate the end-to-end functionality of the Harvest Dashboard once implemented.

## Prerequisites
- Java 21 LTS installed.
- Maven installed.
- Project database available (H2 for local dev/test is sufficient).
- Mock or existing data seeded in the database representing `Products` and `InventoryUnits` (harvests) with varied dates covering multiple months and years.

## Setup & Run

1. **Start the Application:**
   Run the Spring Boot application using Maven:
   ```bash
   mvn spring-boot:run
   ```

2. **Access the System:**
   Open a browser and navigate to the application (default usually `http://localhost:8080`).
   Log in as an `ADMIN` or `MANAGER` user to ensure you have the correct authorization rights.

## Validation Scenarios

### Scenario 1: Verify Lifetime Harvest Section
1. Navigate to the Dashboard menu item (e.g., `/dashboard/harvest`).
2. **Expected Outcome:** The page loads successfully. The "Lifetime Harvest" section is immediately visible.
3. **Verification:** Ensure the listed products and their total quantities mathematically match the total sum of all harvests recorded in the system for those products. The page load should be near-instantaneous.

### Scenario 2: Verify Monthly Snapshot Tab
1. On the dashboard, click the "Monthly Snapshot" tab.
2. Select a specific Month and Year from the dropdowns that corresponds to known seeded data.
3. Click "Apply" or wait for auto-refresh (depending on UI implementation).
4. **Expected Outcome:** The table/chart updates to show only the harvest totals for that specific calendar month.
5. **Verification:** Ensure products not harvested in that month show 0 or are correctly omitted. Select a future month with no data and verify empty states are handled gracefully (e.g., "No harvest data for this period").

### Scenario 3: Verify Yearly Snapshot Tab
1. On the dashboard, click the "Yearly Snapshot" tab.
2. Select a specific Year from the dropdown.
3. **Expected Outcome:** The table/chart updates to show aggregated totals for that entire calendar year.
4. **Verification:** Cross-check the totals manually via a direct SQL query against the H2 console or your database tool to confirm the backend aggregation perfectly matches the raw data sum.

### Scenario 4: Security Verification
1. Log out and attempt to navigate directly to `/dashboard/harvest`.
2. **Expected Outcome:** The system should redirect to the login page or return a `401 Unauthorized` / `403 Forbidden` response.
3. Log in as a standard non-admin user (e.g., standard employee) and attempt to access the URL.
4. **Expected Outcome:** System returns a `403 Forbidden` response.
