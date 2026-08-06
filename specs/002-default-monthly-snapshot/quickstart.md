# Quickstart & Validation Guide: Default Monthly Snapshot

This guide provides the steps to validate the default auto-load behavior of the Monthly Snapshot.

## Prerequisites
- Java 21 LTS installed.
- Maven installed.
- Application running (`mvn spring-boot:run`).
- Logged in as an `ADMIN` or `MANAGER`.

## Validation Scenarios

### Scenario 1: Verify Default Auto-Load
1. Open a browser and navigate directly to `http://localhost:8080/dashboard/harvest` (do not include any `?tab=...` parameters).
2. **Expected Outcome:** The page loads. The "Lifetime Harvest" section is visible at the top. Below it, the tabbed interface shows "Monthly Snapshot" as the active (highlighted) tab. 
3. **Verification:** Ensure the month and year dropdowns are pre-selected to the *current calendar month and year*. The data table should be immediately visible and populated with the harvest totals for this current month without requiring the "Apply Filter" button to be clicked.

### Scenario 2: Verify Explicit Tab Overrides
1. Navigate to `http://localhost:8080/dashboard/harvest?tab=yearly&year=2025`.
2. **Expected Outcome:** The page loads and the "Yearly Snapshot" tab is active, displaying data for 2025. This confirms that explicitly requesting another tab overrides the new `monthly` default.

### Scenario 3: Verify Lifetime Tab Access
1. Navigate to `http://localhost:8080/dashboard/harvest?tab=lifetime`.
2. **Expected Outcome:** The snapshot tabs section (Monthly/Yearly) is hidden, and only the "Lifetime Harvest" section is shown, preserving the original explicit functionality.
