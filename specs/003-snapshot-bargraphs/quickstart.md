# Quickstart & Validation Guide: Snapshot Bargraphs

## Validation Scenarios

### Scenario 1: Monthly Snapshot - Daily Distribution
1. Log in and navigate to `/dashboard/harvest?tab=monthly&month=8&year=2026`.
2. Locate the Bar Graph section. Ensure the UI toggle is set to "Daily".
3. **Verification**: The X-axis should display days (1 through 31). The bars should reflect the total units harvested on each specific day, categorized by product. Hovering over a bar should display a tooltip with exact values.

### Scenario 2: Monthly Snapshot - Dynamic Toggle (Weekly)
1. On the same page as Scenario 1, click the "Weekly" toggle button.
2. **Verification**: The chart should re-render smoothly without the whole page reloading. The X-axis should now display weeks (e.g., "Week 1", "Week 2"). The data should aggregate all harvests within those calendar weeks.

### Scenario 3: Yearly Snapshot - Monthly Distribution
1. Navigate to `/dashboard/harvest?tab=yearly&year=2026`.
2. Locate the Bar Graph section.
3. **Verification**: The X-axis should display the 12 months (Jan-Dec). The bars should reflect the total harvest for each month, categorized by product.
