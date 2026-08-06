# Feature Specification: Snapshot Bargraphs

**Feature Branch**: `[###-snapshot-bargraphs]`

**Created**: 2026-08-06

**Status**: Draft

**Input**: User description: "in the monthly snapshot i need a switchable bargraph to show daily distribution or weekly distribution for that month. and in the yearly section i woul like monthly distribution."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Monthly Snapshot Daily/Weekly Bargraph (Priority: P1)

As a farm manager, I want to view a bar graph in the Monthly Snapshot tab that visualizes harvest totals distributed either by day or by week, so that I can easily spot trends and fluctuations throughout the month.

**Independent Test**: Load the monthly snapshot, verify a bar graph is visible. Toggle between "Daily" and "Weekly" views and verify the X-axis and data points change accordingly to reflect the selected temporal aggregation.

**Acceptance Scenarios**:
1. **Given** the Monthly Snapshot tab is active, **Then** a bar graph is displayed alongside or above the data table.
2. **Given** the bar graph is visible, **Then** a UI toggle is available to switch between "Daily" and "Weekly" aggregation.
3. **Given** the user selects "Daily", **Then** the graph shows the harvest volume for each day of the selected month.
4. **Given** the user selects "Weekly", **Then** the graph aggregates and shows the harvest volume for each week of the selected month.
5. **Given** multiple products exist, **Then** the graph clearly distinguishes between different products (e.g., using grouped bars or stacked bars with a legend).

### User Story 2 - Yearly Snapshot Monthly Bargraph (Priority: P2)

As a farm manager, I want to view a bar graph in the Yearly Snapshot tab that visualizes harvest totals distributed by month, so that I can understand seasonal trends across the year.

**Independent Test**: Load the yearly snapshot and verify a bar graph is visible showing the monthly breakdown of the selected year.

**Acceptance Scenarios**:
1. **Given** the Yearly Snapshot tab is active, **Then** a bar graph is displayed alongside or above the data table.
2. **Given** the bar graph is visible, **Then** the X-axis represents the 12 months of the year.
3. **Given** multiple products exist, **Then** the graph clearly distinguishes between different products for each month.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST aggregate harvest data by day for the selected month and product.
- **FR-002**: The system MUST aggregate harvest data by week for the selected month and product.
- **FR-003**: The system MUST aggregate harvest data by month for the selected year and product.
- **FR-004**: The UI MUST provide a toggle mechanism in the Monthly Snapshot to switch between Daily and Weekly distributions without requiring a full page reload (or if a reload occurs, it preserves the state).
- **FR-005**: The charts MUST support multiple products simultaneously through visual differentiation (e.g., color-coding) and include a legend.

### Key Entities

- Harvest Batch / Inventory Unit (Source of temporal harvest data).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can visually identify the highest and lowest harvest periods within a month or year within 5 seconds of loading the respective tab.
- **SC-002**: The inclusion of charts does not degrade the dashboard load time by more than 500ms.

## Assumptions

- A standard, lightweight charting library will be used to render the graphs.
- "Weekly" distribution within a month will be grouped by standard calendar weeks (e.g., Week 1, Week 2, etc. of that specific month).
