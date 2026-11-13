# Phase 1 Data Model: Adaptive Rolling Procurement Scheduler (ARPS)

**Feature**: `008-adaptive-procurement-scheduler`
**Date**: 2026-11-13

## Entities

### 1. `ProcurementSchedule` (`sk_procurement_schedules`)

Represents a configured procurement item schedule and owns 12 projected `ProcurementCycleNode` records.

| Field | Java Type | Column / Constraints | Description |
|-------|-----------|----------------------|-------------|
| `id` | `Long` | PK, Auto-Increment | Unique identifier |
| `scheduleName` | `String` | `nullable = false`, length 150 | `Schedule_Name` (e.g., `"Raw Substrate - 175kg"`) |
| `supplyLastingDays` | `Integer` | `nullable = false`, `@Min(7)` | `Supply_Lasting_Days` (calendar days inventory lasts, e.g., `28`) |
| `vendorLeadTime` | `Integer` | `nullable = false`, `@Min(1)` | `Vendor_Lead_Time` (minimum calendar days required by vendor, e.g., `15`) |
| `actionBuffer` | `Integer` | `nullable = false`, `@Min(0)` | `Action_Buffer` (safety days before vendor cutoff, e.g., `3`) |
| `preferredDeliveryDay` | `Integer` | `nullable = false`, `@Min(0) @Max(6)` | `Preferred_Delivery_Day` (`0` = Sunday .. `6` = Saturday) |
| `active` | ` boolean` | `nullable = false`, default `true` | Soft-hide / active flag for Hub View |
| `createdAt` | `LocalDateTime` | `nullable = false` | Creation timestamp |
| `version` | `Long` | `@Version` | Optimistic locking version column |
| `nodes` | `List<ProcurementCycleNode>` | `@OneToMany(mappedBy = "schedule", cascade = ALL, orphanRemoval = true)` | Ordered 12-month cycle projections (`cycleIndex = 1..12`) |

### 2. `ProcurementCycleNode` (`sk_procurement_cycle_nodes`)

Represents one cycle (`n = 1..12`) in the 12-month rolling projection for a `ProcurementSchedule`.

| Field | Java Type | Column / Constraints | Description |
|-------|-----------|----------------------|-------------|
| `id` | `Long` | PK, Auto-Increment | Unique identifier |
| `schedule` | `ProcurementSchedule` | FK `schedule_id`, `nullable = false` | Owning schedule |
| `cycleIndex` | `Integer` | `nullable = false` (`1..12`) | Sequence index `n` (Month/Cycle 1 to 12) |
| `theoreticalNeedDate` | `LocalDate` | `nullable = false` | `Theoretical_Need_Date(n) = Actual_Delivery_Date(n-1) + Supply_Lasting_Days` |
| `actualDeliveryDate` | `LocalDate` | `nullable = false` | `Actual_Delivery_Date(n)` (snapped backward to `Preferred_Delivery_Day` or manually overridden) |
| `orderCutoffDate` | `LocalDate` | `nullable = false` | `Order_Cutoff_Date(n) = Actual_Delivery_Date(n) - Vendor_Lead_Time` |
| `unadjustedActionDate` | `LocalDate` | `nullable = false` | `Order_Cutoff_Date(n) - Action_Buffer` prior to holiday blackout shift |
| `actionDate` | `LocalDate` | `nullable = false` | `Action_Date(n)` after subtracting 1–2+ days if falling on `Blackout_Dates` |
| `manualOverride` | `boolean` | `nullable = false`, default `false` | `true` when user manually overrode `actualDeliveryDate` |
| `gapDays` | `Integer` | `nullable = false` | Days between `Actual_Delivery_Date(n)` and `Actual_Delivery_Date(n-1)` |
| `stockoutRisk` | `boolean` | `nullable = false`, default `false` | `true` if `gapDays > schedule.supplyLastingDays` |
| `holidayAdjusted` | `boolean` | `nullable = false`, default `false` | `true` if `actionDate` was shifted earlier due to a holiday blackout |
| `holidayAdjustedDays` | `Integer` | `nullable = false`, default `0` | Number of days subtracted from `unadjustedActionDate` |

### 3. Global Holiday Blackout Setting (`AppSetting`)

Stored in `sk_app_settings` under key `procurement_blackout_dates`:
- Format: Comma- or newline-separated ISO date entries `YYYY-MM-DD` (optionally with label `YYYY-MM-DD|Holiday Name`, e.g., `2026-12-25|Christmas,2026-08-26|Onam`).
- Managed via `SettingsService` (`getProcurementBlackoutDates()`, `setProcurementBlackoutDates(String)`).

## Validation & State Invariants

1. **Schedule Creation Invariants**:
   - `scheduleName` MUST NOT be blank.
   - `supplyLastingDays >= 7` (prevents backward weekly snap from landing on or before the prior cycle's delivery date).
   - `vendorLeadTime >= 1`.
   - `actionBuffer >= 0`.
   - `0 <= preferredDeliveryDay <= 6`.
2. **Lead Time Collision Invariant**:
   - Manual delivery override `newDeliveryDate` MUST satisfy `newDeliveryDate >= LocalDate.now().plusDays(vendorLeadTime)`.
   - Otherwise, reject with `"Invalid Date: Vendor requires {vendorLeadTime} days lead time. Earliest possible delivery is {earliestDate}."`
3. **Stockout Gap Invariant**:
   - For any cycle `n > 1`, `gapDays = ChronoUnit.DAYS.between(node(n-1).actualDeliveryDate, node(n).actualDeliveryDate)`.
   - If `gapDays > supplyLastingDays`, `stockoutRisk = true`, and the node exposes warning message:
     `"Stockout Risk: Gap is {gapDays} days, supply only lasts {supplyLastingDays}. Increase order volume or pull delivery forward."`
4. **Holiday Blackout Invariant**:
   - If `unadjustedActionDate` is in `Blackout_Dates`, subtract 1 day repeatedly until `actionDate` is not in `Blackout_Dates`.
