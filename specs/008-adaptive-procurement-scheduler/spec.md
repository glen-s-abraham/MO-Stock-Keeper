# Feature Specification: Adaptive Rolling Procurement Scheduler (ARPS)

**Feature Branch**: `[008-adaptive-procurement-scheduler]`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "i need a new feature in this with the following spec give anew tab menu for this as well. FEATURE SPECIFICATION: Adaptive Rolling Procurement Scheduler (ARPS)1. Objective: To provide a dynamic, 12-month forward-looking procurement calendar that automatically recalculates vendor order dates and delivery dates based on manual delivery overrides, fixed lead times, and preferred logistical days.2. Core Data Model (The Inputs): To create a new Schedule instance, the user inputs the following parameters into a configuration modal: ParameterData TypeExample ValueBusiness Logic / DefinitionSchedule_NameString\"Raw Substrate - 175kg\"Identifier for the schedule.Supply_Lasting_DaysInteger28The actual calendar days the inventory will last. (Note: If you run 22 working days, that equals ~28 calendar days).Vendor_Lead_TimeInteger15Minimum days required by the vendor to fulfill the order.Action_BufferInteger3Safety days added before the vendor cutoff to remind the user to place the order.Preferred_Delivery_DayEnum (0-6)6 (Saturday)The strict day of the week logistics can actually be executed.3. The Algorithmic Engine (The \"Ripple\" Logic): The core engine runs a cascading loop (n=1 to 12) to project the next 12 months. When a user manually updates Month 1's delivery date, the algorithm recalculates Months 2 through 12 sequentially.Step 1: Determine Next Theoretical Need:Theoretical_Need_Date(n) = Actual_Delivery_Date(n-1) + Supply_Lasting_DaysStep 2: Snap to Logistics Window:Actual_Delivery_Date(n) = Find the next occurring Preferred_Delivery_Day on or immediately before the Theoretical_Need_Date. (Crucial: Snap backwards, not forwards, to prevent stockouts).Step 3: Calculate Vendor Cutoff:Order_Cutoff_Date(n) = Actual_Delivery_Date(n) - Vendor_Lead_TimeStep 4: Calculate Internal Alert:Action_Date(n) = Order_Cutoff_Date(n) - Action_Buffer4. The User Interface (UI/UX) Execution:The Hub View: A clean dashboard displaying the current month's active schedules. It should highlight the Action_Date in Yellow (Upcoming) or Red (Overdue).The Timeline View: A 12-month vertically scrolling timeline or Kanban-style list showing [Action Date] -> [Cutoff Date] -> [Delivery Date] for the selected item.The Edit Override: A simple \"Edit Delivery Date\" calendar picker next to each node.The Feedback Loop: When the user changes a date, a toast notification appears: \"Delivery shifted by X days. Subsequent 12 months successfully recalculated.\"5. Edge Case & Operational Redline Protections (Critical for the Dev Spec):Edge CaseThe Software Solution (Business Logic)The Stockout GapBecause the algorithm \"snaps\" to a preferred day (e.g., Saturday), the gap between deliveries might exceed the Supply_Lasting_Days. Logic: If (Delivery_Date_2 - Delivery_Date_1) > Supply_Lasting_Days, the UI must flag the node in Orange with a warning: \"Stockout Risk: Gap is 30 days, supply only lasts 28. Increase order volume or pull delivery forward.\"The Lead Time CollisionIf a user manually changes a delivery date to be sooner than the Vendor_Lead_Time allows (e.g., changing delivery to next Friday, but lead time is 15 days). Logic: Block the UI edit and throw an error: \"Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is [Date].\"Holiday BlackoutsLogic: Allow a global settings array for Blackout_Dates (e.g., Christmas, Onam). If the calculated Action_Date falls on a blackout, the algorithm automatically subtracts 1-2 days to ensure the order is placed before the holiday."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create Procurement Schedule & View 12-Month Rolling Timeline (Priority: P1)

As an operations manager, I want to navigate to a dedicated Procurement Scheduler tab in the main menu, create a new procurement schedule via a configuration modal with supply duration, vendor lead time, action buffer, and preferred delivery day of the week, and view the generated 12-cycle forward-looking timeline (`[Action Date] -> [Cutoff Date] -> [Delivery Date]`) so that I know exactly when to initiate orders and receive deliveries over the next year.

**Why this priority**: Core capability that establishes the procurement schedule data model, navigation tab, and the 12-cycle cascading calculation engine. Without this, no schedules can be created or viewed.

**Independent Test**: Can be fully tested by clicking the new Procurement Scheduler navigation tab, opening the configuration modal, entering `"Raw Substrate - 175kg"`, `Supply_Lasting_Days = 28`, `Vendor_Lead_Time = 15`, `Action_Buffer = 3`, and `Preferred_Delivery_Day = Saturday (6)`, saving the schedule, and verifying that 12 sequential cycles are generated where every automatic delivery date lands on a Saturday on or immediately before the theoretical need date, with cutoff and action dates accurately offset.

**Acceptance Scenarios**:

1. **Given** an authorized user is logged into the application, **When** they inspect the primary navigation menu, **Then** a dedicated tab for the Procurement Scheduler is visible and navigates to the Adaptive Rolling Procurement Scheduler view.
2. **Given** the user opens the "New Schedule" configuration modal and submits `Schedule_Name = "Raw Substrate - 175kg"`, `Supply_Lasting_Days = 28`, `Vendor_Lead_Time = 15`, `Action_Buffer = 3`, and `Preferred_Delivery_Day = 6 (Saturday)`, **When** the schedule is created, **Then** the system projects 12 sequential procurement cycles (`n = 1` to `12`) where for each subsequent cycle `n`, `Theoretical_Need_Date(n) = Actual_Delivery_Date(n-1) + 28`, `Actual_Delivery_Date(n)` snaps backward to the nearest Saturday on or before `Theoretical_Need_Date(n)`, `Order_Cutoff_Date(n) = Actual_Delivery_Date(n) - 15`, and `Action_Date(n) = Order_Cutoff_Date(n) - 3`.
3. **Given** a created schedule is selected, **When** the user views the Timeline View, **Then** a 12-month vertically scrolling timeline or Kanban-style list displays `[Action Date] -> [Cutoff Date] -> [Delivery Date]` for all 12 cycles in chronological order.

---

### User Story 2 - Manual Delivery Date Override & Cascading "Ripple" Recalculation (Priority: P1)

As an operations manager dealing with real-world supplier or logistics shifts, I want to manually edit the delivery date of a scheduled cycle using a calendar picker so that all subsequent cycles through cycle 12 automatically recalculate in a cascading ripple, with immediate visual feedback confirming the shift and warning me of lead-time collisions or stockout gaps.

**Why this priority**: The adaptive "ripple" recalculation and operational redline validations (lead-time collision blocking and stockout risk flagging) are the primary business value differentiators of the scheduler.

**Independent Test**: Can be tested by selecting an existing 12-cycle schedule, using the "Edit Delivery Date" calendar picker on Cycle 1 to shift the delivery date by +4 days, and verifying the toast notification `"Delivery shifted by 4 days. Subsequent 12 months successfully recalculated."` appears and Cycles 2 through 12 update their delivery, cutoff, and action dates accordingly.

**Acceptance Scenarios**:

1. **Given** a schedule with 12 projected cycles, **When** the user updates Cycle 1's delivery date using the "Edit Delivery Date" calendar picker to a valid date shifted by `X` days, **Then** Cycles 2 through 12 are sequentially recalculated starting from the new Cycle 1 delivery date, and a toast notification displays: `"Delivery shifted by X days. Subsequent 12 months successfully recalculated."`
2. **Given** a schedule with `Vendor_Lead_Time = 15` days, **When** the user attempts to change a cycle's delivery date to a date earlier than `Today + 15 days` (or fewer than `Vendor_Lead_Time` days from the reference order window), **Then** the system blocks the edit and displays the error message: `"Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is [Date]."`
3. **Given** a schedule with `Supply_Lasting_Days = 28`, **When** a delivery date override results in the gap between consecutive deliveries `(Delivery_Date(n) - Delivery_Date(n-1))` exceeding `28` days (e.g., `30` days), **Then** the UI flags that cycle node in **Orange** with the warning: `"Stockout Risk: Gap is 30 days, supply only lasts 28. Increase order volume or pull delivery forward."`

---

### User Story 3 - Hub View Dashboard with Action Date Status Alerts (Priority: P2)

As an operations manager reviewing daily and monthly procurement priorities, I want a clean Hub View dashboard displaying active schedules for the current month with `Action_Date` highlighted in **Yellow** (Upcoming) or **Red** (Overdue) so that I never miss an internal order placement deadline.

**Why this priority**: Provides immediate situational awareness across multiple procurement schedules at a glance before drilling into a single schedule's 12-month timeline.

**Independent Test**: Can be tested by creating schedules whose current cycle `Action_Date` is in the past (before today) and in the near future (within the upcoming alert window), opening the Hub View, and verifying that overdue action dates are highlighted in Red and upcoming action dates are highlighted in Yellow.

**Acceptance Scenarios**:

1. **Given** multiple active procurement schedules exist, **When** the user opens the Procurement Scheduler Hub View, **Then** the dashboard lists the current month's active schedules along with their next `Action_Date`, `Order_Cutoff_Date`, and `Actual_Delivery_Date`.
2. **Given** an active schedule whose current cycle `Action_Date` is today or within the upcoming reminder window (and not yet past), **When** displayed on the Hub View, **Then** its `Action_Date` status is highlighted in **Yellow (Upcoming)**.
3. **Given** an active schedule whose current cycle `Action_Date` is earlier than today, **When** displayed on the Hub View, **Then** its `Action_Date` status is highlighted in **Red (Overdue)**.

---

### User Story 4 - Global Holiday Blackout Date Adjustment (Priority: P2)

As an operations manager, I want to configure global holiday blackout dates (such as Christmas or Onam) so that whenever a calculated `Action_Date` falls on a blackout date, the scheduler automatically shifts the `Action_Date` backward by 1–2 days to ensure the vendor order is placed before the holiday.

**Why this priority**: Protects procurement continuity during public or company holidays when internal staff or vendors are unavailable to process orders on the standard action date.

**Independent Test**: Can be tested by adding a holiday date to the global `Blackout_Dates` settings that matches a schedule cycle's normal `Action_Date`, recalculating or viewing the schedule, and verifying that the cycle's `Action_Date` is automatically moved backward to the preceding non-blackout day before the holiday.

**Acceptance Scenarios**:

1. **Given** the global `Blackout_Dates` setting contains a holiday date `H`, **When** a cycle's unadjusted `Action_Date` (`Order_Cutoff_Date - Action_Buffer`) lands on `H`, **Then** the engine automatically subtracts days (1–2 days, or to the nearest preceding non-blackout day) so that `Action_Date` falls before the holiday blackout period.
2. **Given** an administrator or manager updates the global `Blackout_Dates` list (adding or removing holiday dates), **When** active schedules are viewed or recalculated, **Then** any cycle whose `Action_Date` collides with a configured blackout date reflects the pre-holiday adjusted `Action_Date`.

---

### Edge Cases

- **Backward Snapping Across Month/Year Boundaries**: When `Theoretical_Need_Date(n)` falls early in a month or in January of the next year, snapping backward to `Preferred_Delivery_Day` MUST accurately cross month and year boundaries (including leap years) without off-by-one errors.
- **Exact Day Match on Preferred Delivery Day**: If `Theoretical_Need_Date(n)` already falls on `Preferred_Delivery_Day`, `Actual_Delivery_Date(n)` MUST equal `Theoretical_Need_Date(n)` (0 days snapped) rather than snapping a full 7 days backward.
- **Short Supply Duration vs Weekly Logistics Window**: If a user configures `Supply_Lasting_Days < 7`, snapping backward to a strict day of the week could cause `Actual_Delivery_Date(n)` to be on or before `Actual_Delivery_Date(n-1)`. The configuration modal MUST validate that `Supply_Lasting_Days` is at least `7` days (and `Vendor_Lead_Time >= 1`, `Action_Buffer >= 0`).
- **Mid-Timeline Override (`n > 1`)**: When a user manually edits the delivery date of Cycle `k` (where `1 <= k <= 12`), Cycles `1` through `k-1` remain unchanged, Cycle `k` uses the user's overridden delivery date (validated against lead-time collision and evaluated for stockout gap against Cycle `k-1`), and Cycles `k+1` through `12` cascade sequentially from Cycle `k`.
- **Consecutive Multi-Day Holiday Blackouts**: If a holiday blackout spans 2 consecutive days (or a holiday immediately follows another blackout date) and `Action_Date` lands on the second day, the adjustment subtracts up to 2 days (until a non-blackout calendar day is reached) so the action date never lands on another blackout date.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a dedicated top-level navigation tab in the application menu to access the Adaptive Rolling Procurement Scheduler (ARPS).
- **FR-002**: System MUST allow users to create (and edit/delete) Procurement Schedule instances via a configuration modal capturing:
  - `Schedule_Name` (non-empty text identifier, e.g., `"Raw Substrate - 175kg"`)
  - `Supply_Lasting_Days` (integer `>= 7`, representing calendar days inventory lasts, e.g., `28`)
  - `Vendor_Lead_Time` (integer `>= 1`, minimum calendar days required by vendor to fulfill an order, e.g., `15`)
  - `Action_Buffer` (integer `>= 0`, safety days before vendor cutoff to alert user, e.g., `3`)
  - `Preferred_Delivery_Day` (day-of-week enumeration `0` to `6` corresponding to Sunday through Saturday, e.g., `6` for Saturday)
  - Optional initial anchor delivery date for Cycle 1 (defaulting to the earliest occurring `Preferred_Delivery_Day` on or after `Today + Vendor_Lead_Time`).
- **FR-003**: System MUST project a 12-cycle (`n = 1` to `12`) forward-looking procurement schedule for each Schedule instance using the following cascading sequential algorithm for each subsequent cycle `n`:
  - **Step 1 (Theoretical Need)**: `Theoretical_Need_Date(n) = Actual_Delivery_Date(n-1) + Supply_Lasting_Days`
  - **Step 2 (Snap to Logistics Window)**: `Actual_Delivery_Date(n)` = the occurrence of `Preferred_Delivery_Day` on or immediately **before** `Theoretical_Need_Date(n)` (snapping backward, never forward).
  - **Step 3 (Vendor Cutoff)**: `Order_Cutoff_Date(n) = Actual_Delivery_Date(n) - Vendor_Lead_Time`
  - **Step 4 (Internal Alert)**: `Action_Date(n) = Order_Cutoff_Date(n) - Action_Buffer`, adjusted backward if it falls on a configured global Holiday Blackout date.
- **FR-004**: System MUST provide a **Hub View** dashboard displaying active schedules for the current month, highlighting each schedule's active `Action_Date` in **Yellow** when Upcoming (within the upcoming alert window, default 7 days including today) and in **Red** when Overdue (`Action_Date < Today`).
- **FR-005**: System MUST provide a **Timeline View** displaying the 12 projected cycles for a selected schedule as a vertically scrolling timeline or Kanban-style sequence clearly showing `[Action Date] -> [Cutoff Date] -> [Delivery Date]` for each cycle node.
- **FR-006**: System MUST provide an **"Edit Delivery Date"** calendar picker on each cycle node allowing the user to manually override `Actual_Delivery_Date(k)` for cycle `k`, which then recalculates `Order_Cutoff_Date(k)` and `Action_Date(k)` for cycle `k` and cascades Steps 1–4 across all subsequent cycles `k+1` through `12`.
- **FR-007**: Upon a successful manual delivery date override shifting the delivery date by `X` days, the system MUST display a toast notification: `"Delivery shifted by X days. Subsequent 12 months successfully recalculated."`
- **FR-008**: **Stockout Gap Protection**: Whenever the calendar gap between two consecutive cycles exceeds the configured supply duration (`Actual_Delivery_Date(n) - Actual_Delivery_Date(n-1) > Supply_Lasting_Days`, where `G` is the gap in days and `S` is `Supply_Lasting_Days`), the UI MUST highlight the affected cycle node in **Orange** and display the warning message: `"Stockout Risk: Gap is [G] days, supply only lasts [S]. Increase order volume or pull delivery forward."`
- **FR-009**: **Lead Time Collision Protection**: Whenever a user attempts to set a subsequent replenishment cycle's (`n >= 2`) delivery date earlier than allowed by the vendor lead time (`Selected_Date < Today + Vendor_Lead_Time`), the system MUST block the edit and display the error message: `"Invalid Date: Vendor requires [Vendor_Lead_Time] days lead time. Earliest possible delivery is [Earliest_Date]."` Month 1 (`n = 1`) represents the initial received or anticipated anchor delivery date and MUST allow any user-selected date without lead-time restriction, cascading Months 2 through 12 from that anchor.
- **FR-010**: **Holiday Blackout Protection**: System MUST support a global settings list of `Blackout_Dates` (e.g., Christmas, Onam). When a calculated `Action_Date(n)` falls on a configured blackout date, the algorithm MUST automatically subtract 1–2 days (stepping backward to the latest non-blackout date prior to the holiday) so the order action occurs before the holiday.
- **FR-011**: All procurement schedules, cycle overrides, and global holiday blackout dates MUST be persisted so they remain intact across user sessions and system restarts.

### Key Entities *(include if feature involves data)*

- **ProcurementSchedule**: Represents a recurring procurement item configuration, containing `Schedule_Name`, `Supply_Lasting_Days`, `Vendor_Lead_Time`, `Action_Buffer`, `Preferred_Delivery_Day` (0–6), active status, and its sequence of 12 scheduled cycle nodes.
- **ProcurementCycleNode**: Represents a single projected month/cycle (`n = 1..12`) within a `ProcurementSchedule`, holding `Cycle_Index` (`1..12`), `Theoretical_Need_Date`, `Actual_Delivery_Date`, `Order_Cutoff_Date`, `Action_Date`, whether the delivery date was manually overridden, and computed risk flags (`Stockout_Risk`, `Gap_Days`, `Holiday_Adjusted`).
- **BlackoutDateSetting**: Global operational configuration storing calendar dates designated as holiday blackouts (e.g., Christmas, Onam) along with an optional holiday label.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new procurement schedule with all 5 core parameters and generate a complete 12-cycle rolling projection in under 30 seconds.
- **SC-002**: 100% of automatically calculated delivery dates across all 12 cycles fall on the configured `Preferred_Delivery_Day` and on or before the cycle's `Theoretical_Need_Date`.
- **SC-003**: Overriding any cycle's delivery date recalculates all downstream cycles and renders the updated 12-month timeline and toast feedback notification in under 1 second.
- **SC-004**: 100% of manual delivery date edits that violate `Vendor_Lead_Time` are blocked with the exact earliest possible delivery date shown, and 100% of cycle gaps exceeding `Supply_Lasting_Days` are flagged in Orange with the exact gap and lasting days values.
- **SC-005**: 100% of calculated `Action_Date` values that land on a configured global blackout date are automatically shifted to precede the holiday blackout.

## Assumptions

- **Cycle 1 Anchor Date**: When creating a new schedule, users may optionally specify the first delivery date (`Actual_Delivery_Date(1)`); if left blank, the system defaults `Actual_Delivery_Date(1)` to the first occurrence of `Preferred_Delivery_Day` on or after `Today + Vendor_Lead_Time + Action_Buffer` so Cycle 1 is immediately actionable without violating lead times.
- **Upcoming vs Overdue Highlight Thresholds**: In the Hub View, an `Action_Date` is highlighted **Red (Overdue)** when `Action_Date < Today`, and **Yellow (Upcoming)** when `Action_Date` is between `Today` and `Today + 7 days` (or within the current month's actionable window).
- **Day-of-Week Enum Mapping**: `Preferred_Delivery_Day` uses standard `0` = Sunday, `1` = Monday, `2` = Tuesday, `3` = Wednesday, `4` = Thursday, `5` = Friday, `6` = Saturday (matching the user example `6 (Saturday)`).
- **Access Control**: Procurement Scheduler views and configuration follow the application's standard authenticated user access rules.
