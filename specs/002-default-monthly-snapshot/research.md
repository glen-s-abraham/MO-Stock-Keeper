# Phase 0: Research & Technical Decisions

## 1. Setting Default Tab and Timeframe
- **Context**: The Harvest Dashboard currently requires user interaction to select the Monthly tab. We need it to default to the Monthly tab for the current month/year on initial load.
- **Decision**: Modify the `@RequestParam` for `tab` in `DashboardController` to have `defaultValue = "monthly"` instead of `"lifetime"`. Ensure the `month` and `year` parameters already default to `LocalDate.now()` if null (which was implemented in the previous feature).
- **Rationale**: Utilizing Spring Web's built-in parameter defaults (`defaultValue`) is the cleanest, most standard way to control the initial view without relying on client-side JavaScript redirects or messy conditional logic in Thymeleaf.
- **Alternatives considered**:
  - Client-side redirect via JavaScript (rejected due to flickering and unnecessary second network request).
  - Changing the URL routing (rejected as it complicates the simple single-view structure).

## 2. UI Structure Adjustment
- **Context**: The "Lifetime Totals" table is currently visible at the top, and the "Snapshot" table is at the bottom. If "Monthly" is active by default, both will be visible.
- **Decision**: Keep both visible (Lifetime at top, Monthly snapshot at bottom). This provides maximum context. The `activeTab` variable logic in Thymeleaf handles this cleanly.
- **Rationale**: Minimal code change while delivering exactly what the user requested.
