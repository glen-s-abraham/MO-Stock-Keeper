# Feature Specification: Default Monthly Snapshot

**Feature Branch**: `[###-default-monthly-snapshot]`

**Created**: 2026-08-06

**Status**: Draft

**Input**: User description: "one ehnancement i would like to make is that the monthly snapshot should be active and deafault populated to the current month when harest dashboard is loaded. at present the user interaction is needed as trigger"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Auto-load Current Month Snapshot (Priority: P1)

As a farm manager, I want the Harvest Dashboard to automatically load and display the Monthly Snapshot tab for the current month when I first navigate to the page, so that I don't have to manually click the tab and apply filters to see the most relevant immediate data.

**Why this priority**: The current month's performance is the most frequently checked metric. Automating its display removes unnecessary friction and clicks for the user.

**Independent Test**: Can be fully tested by navigating to the dashboard URL (`/dashboard/harvest`) without any query parameters and verifying that the "Monthly Snapshot" tab is active by default and populated with data for the current calendar month and year.

**Acceptance Scenarios**:

1. **Given** the user navigates directly to the dashboard root, **When** the page loads, **Then** the "Monthly Snapshot" tab is visually active instead of the "Lifetime" or "Yearly" tab (or displayed alongside lifetime totals depending on layout, but the snapshot section defaults to Monthly).
2. **Given** the page loads by default, **When** viewing the snapshot section, **Then** the dropdowns for Month and Year are pre-selected to the current calendar month and year.
3. **Given** the page loads by default, **Then** the data table automatically displays the aggregated harvest quantities for that current month without requiring the user to click "Apply Filter".
4. **Given** the user explicitly requests another tab via URL parameters (e.g., `?tab=lifetime` or `?tab=yearly`), **When** the page loads, **Then** the requested tab is active, respecting the explicit parameter over the default.

### Edge Cases

- What happens if the system clock/timezone calculation for the "current month" differs significantly from the user's local timezone? (Assume server-local time is acceptable for the default).
- What if there is absolutely no harvest data for the current month yet (e.g., it is the 1st of the month)? (Should display the standard empty state gracefully, e.g., "No harvest data available for this period").

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST default the `tab` parameter to `monthly` if no tab is explicitly provided in the request to the harvest dashboard.
- **FR-002**: System MUST automatically fetch and inject the `snapshotTotals` data for the current month and year when the dashboard loads with the default `monthly` tab.
- **FR-003**: System MUST retain the ability to view other tabs if explicitly requested via query parameters.

### Key Entities

- N/A - This feature modifies the default presentation logic of existing data models.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Number of clicks required to view the current month's harvest data drops from 2 (click tab, click apply) to 0 upon initial page load.
- **SC-002**: Page load time for the dashboard (including the default monthly data fetch) remains under 2 seconds.

## Assumptions

- "Lifetime totals" are still displayed on the page as a separate upper section, but the tabbed section at the bottom defaults to "Monthly" instead of waiting for user interaction.
- Server-side rendering (Thymeleaf) will be updated to fetch and supply this data on the initial GET request without requiring a separate AJAX call.
