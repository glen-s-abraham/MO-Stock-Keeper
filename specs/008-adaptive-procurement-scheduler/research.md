# Phase 0 Research: Adaptive Rolling Procurement Scheduler (ARPS)

**Feature**: `008-adaptive-procurement-scheduler`
**Date**: 2026-11-13

## 1. Backward Logistics Window Snapping (`java.time`)

- **Decision**: Map the specification's `Preferred_Delivery_Day` enum (`0` = Sunday through `6` = Saturday) to `java.time.DayOfWeek` (`0 -> DayOfWeek.SUNDAY`, `1..6 -> DayOfWeek.of(value)`) and compute `Actual_Delivery_Date(n)` via `theoreticalNeedDate.with(TemporalAdjusters.previousOrSame(targetDayOfWeek))`.
- **Rationale**: `TemporalAdjusters.previousOrSame(DayOfWeek)` guarantees backward snapping (on or immediately before `Theoretical_Need_Date(n)`), never snapping forward (which would cause stockouts), and handles month-end, year-end, and leap-year boundaries natively in the JDK without external libraries.
- **Alternatives considered**: Manual modular day-of-week arithmetic (`(currentDow - targetDow + 7) % 7`). While mathematically equivalent, `TemporalAdjusters.previousOrSame` is self-documenting and less error-prone.

## 2. Persistence & Concurrency Architecture

- **Decision**: Introduce two JPA entities (`ProcurementSchedule` mapped to `sk_procurement_schedules` with `@Version` optimistic locking, and `ProcurementCycleNode` mapped to `sk_procurement_cycle_nodes`) and store global `Blackout_Dates` in `AppSetting` (`key = "procurement_blackout_dates"`) via `SettingsService`.
- **Rationale**: Matches existing MO-Stock-Keeper conventions (`sk_` table prefix, Lombok `@Data`, JPA repositories, `@Version` optimistic locking per Constitution Principle IV, and soft-delete / active flag support). Storing `Blackout_Dates` in `AppSetting` allows configuring holiday blackouts both in the Procurement Scheduler UI and in `/admin/settings` without requiring an extra settings table.
- **Alternatives considered**: Computing the 12 nodes purely transiently on every GET request without persisting nodes. Rejected because persisting the 12 `ProcurementCycleNode` records preserves manual delivery overrides (`Actual_Delivery_Date`), `gapDays`, and `stockoutRisk` flags across sessions and allows efficient Hub View queries.

## 3. UI/UX Execution (Hub View, 12-Month Timeline View, Edit Override, Feedback Loop)

- **Decision**: Provide a dedicated `/procurement` controller (`ProcurementController`) and Thymeleaf view (`templates/procurement/index.html`) linked from a new top-level `Procurement` tab in `templates/layout/base.html`. The page renders:
  1. **The Hub View**: Dashboard cards/table of active schedules showing current month's / active cycle's `Action_Date` badged in **Red (Overdue)** when `actionDate < today` or **Yellow (Upcoming)** when `actionDate >= today` within the upcoming window (or current month), alongside Cutoff Date, Delivery Date, and Stockout Risk status.
  2. **The Timeline View**: A 12-month vertically scrolling timeline / Kanban-style node list for the selected schedule showing `[Action Date] -> [Cutoff Date] -> [Delivery Date]` for cycles `n = 1..12`, flagging any cycle where `gapDays > supplyLastingDays` in **Orange** with the exact warning text.
  3. **The Edit Override & Feedback Loop**: An inline `"Edit Delivery Date"` calendar picker on each node that validates against `Vendor_Lead_Time` (`earliestAllowed = today + vendorLeadTime`), blocks invalid dates with `"Invalid Date: Vendor requires X days lead time. Earliest possible delivery is [Date]."`, and on valid change triggers cascading recalculation and displays a Bootstrap Toast notification: `"Delivery shifted by X days. Subsequent 12 months successfully recalculated."`
- **Rationale**: Uses the existing Bootstrap 5.3 + Bootstrap Icons stack already bundled locally in `layout/base.html` (`bootstrap.bundle.min.js`, `bootstrap.min.css`), requiring zero new frontend framework dependencies.
- **Alternatives considered**: Separate multi-page flow for Hub vs Timeline. Combining Hub View at the top and the selected schedule's 12-Month Timeline View directly below (with schedule selector tabs/links and configuration modals) minimizes clicks and keeps the entire workflow on the new tab.
