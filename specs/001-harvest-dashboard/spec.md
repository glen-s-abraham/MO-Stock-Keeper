# Feature Specification: Harvest Visual Dashboard

**Feature Branch**: `[###-harvest-dashboard]`

**Created**: 2026-08-06

**Status**: Draft

**Input**: User description: "i would like to bring in an actual visual dashboard. for the time being the dashboard should show a section with total lifetime harvest of each available products like from the commencement of the system. then the second section should be a tabbed section one tab should be a monthly snapshot like the monthly total of each product month/year drop down to fetch corresponding data. and the second tab should be yearly data like a yearly dropdown to show yearly harvestof the products."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lifetime Harvest Overview (Priority: P1)

As a farm manager or system administrator, I want to see the total lifetime harvest for each available product since the commencement of the system, so that I can quickly understand overall productivity.

**Why this priority**: Lifetime data provides the most fundamental, high-level view of system productivity and is the primary section the user expects to see upon loading the dashboard.

**Independent Test**: Can be fully tested by loading the dashboard and verifying the displayed lifetime totals for each product against known historical harvest records in the database.

**Acceptance Scenarios**:

1. **Given** there are existing harvest records for various products in the system, **When** the user accesses the visual dashboard, **Then** a dedicated section displays the total accumulated harvest quantity for each available product.
2. **Given** a product has zero recorded harvests, **When** the user accesses the visual dashboard, **Then** that product either displays a zero total or is safely omitted depending on UI design.
3. **Given** a large volume of harvest history, **When** the dashboard loads, **Then** the totals are aggregated efficiently (natively in the database per Constitution Principle V) and display without significant delay.

---

### User Story 2 - Monthly Harvest Snapshot (Priority: P2)

As a farm manager, I want to view a monthly snapshot showing the total harvest of each product for a specific month and year, so that I can analyze short-term seasonal performance.

**Why this priority**: Granular monthly data allows for tracking immediate performance trends and comparing recent yields.

**Independent Test**: Can be fully tested by selecting different month/year combinations and verifying the displayed totals match the harvest data restricted to those specific timeframes.

**Acceptance Scenarios**:

1. **Given** the dashboard is loaded, **When** the user navigates to the tabbed section and selects the "Monthly Snapshot" tab, **Then** month and year dropdown selectors are presented.
2. **Given** the "Monthly Snapshot" tab is active, **When** the user selects a specific month and year from the dropdowns, **Then** the dashboard fetches and displays the total harvest for each product during that exact calendar month.
3. **Given** a selected month has no harvest data, **When** the data is fetched, **Then** the dashboard clearly indicates that there was no harvest for that period.

---

### User Story 3 - Yearly Harvest Snapshot (Priority: P3)

As a farm manager, I want to view a yearly snapshot showing the total harvest of each product for a specific year, so that I can perform long-term, year-over-year performance analysis.

**Why this priority**: Annual aggregation provides a macro view of farm productivity, essential for long-term planning, though slightly less critical for daily operations than monthly tracking.

**Independent Test**: Can be fully tested by selecting different years and verifying the totals match the annual aggregates.

**Acceptance Scenarios**:

1. **Given** the dashboard is loaded, **When** the user navigates to the tabbed section and selects the "Yearly Snapshot" tab, **Then** a year dropdown selector is presented.
2. **Given** the "Yearly Snapshot" tab is active, **When** the user selects a specific year, **Then** the dashboard fetches and displays the total harvest for each product during that entire year.

### Edge Cases

- What happens when a product has zero harvest data for a selected period? (Should display 0 or "No Data" gracefully).
- How does the system handle time zone differences if harvests are recorded across different boundaries? (Assume server-local or single consistent timezone for all aggregations).
- What happens if the dashboard is loaded but there is absolutely no product or harvest data in the entire system? (Should display empty states without erroring).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a dedicated UI view/page for the Visual Dashboard.
- **FR-002**: System MUST display a "Lifetime Harvest" section showing aggregated total quantities per product from the beginning of recorded history.
- **FR-003**: System MUST display a tabbed interface containing at least two tabs: "Monthly Snapshot" and "Yearly Snapshot".
- **FR-004**: System MUST provide Month and Year dropdown selectors in the "Monthly Snapshot" tab.
- **FR-005**: System MUST fetch and display product harvest totals filtered by the selected month and year upon selection.
- **FR-006**: System MUST provide a Year dropdown selector in the "Yearly Snapshot" tab.
- **FR-007**: System MUST fetch and display product harvest totals filtered by the selected year upon selection.
- **FR-008**: System MUST perform all aggregation calculations natively within the database engine (e.g., using SQL `GROUP BY`), adhering to project Constitution Principle V.

### Key Entities *(include if feature involves data)*

- **Product/Inventory**: Represents the agricultural products available in the system.
- **Harvest/InventoryUnit**: The records representing actual harvested amounts and timestamps which will be aggregated.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can load the dashboard and view the lifetime harvest totals within 2 seconds, regardless of the underlying data volume.
- **SC-002**: Users can toggle between the Monthly and Yearly tabs instantly, and subsequent data fetches based on dropdown selections complete within 1 second.
- **SC-003**: The visual dashboard is successfully integrated into the main navigation structure of the application.
- **SC-004**: All data displayed on the dashboard precisely matches the raw, unaggregated records in the database.

## Assumptions

- The underlying data schema already tracks individual harvest events or inventory additions with accurate timestamps and quantities.
- The UI will use standard charting or structured table components appropriate for a "visual dashboard", though the exact visual library is left to implementation.
- The "products" referred to align with existing product or crop definitions in the system.
- Standard role-based access control (RBAC) applies; only authorized users (e.g., ADMIN or MANAGER roles) can view the dashboard.
