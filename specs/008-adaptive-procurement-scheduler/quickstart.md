# Quickstart Validation Guide: Adaptive Rolling Procurement Scheduler (ARPS)

**Feature**: `008-adaptive-procurement-scheduler`
**Date**: 2026-11-13

## Prerequisites

- Java 21 LTS
- Maven (`mvn`)

## Running Automated Verification

Run the unit and controller integration tests for the Adaptive Rolling Procurement Scheduler:

```bash
mvn test -Dtest=ProcurementSchedulerServiceTest,ProcurementControllerTest
```

Run the full test suite to verify zero regressions across the application:

```bash
mvn test
```

## End-to-End Validation Scenarios

1. **Create Schedule & Verify 12-Month Backward-Snapping Projection**:
   - Start the application (`mvn spring-boot:run`) and log in.
   - Click the **Procurement** tab in the top navigation bar (`/procurement`).
   - Click **New Schedule** and enter:
     - `Schedule Name`: `Raw Substrate - 175kg`
     - `Supply Lasting Days`: `28`
     - `Vendor Lead Time`: `15`
     - `Action Buffer`: `3`
     - `Preferred Delivery Day`: `Saturday (6)`
   - Submit and verify that 12 cycles (`Month 1` to `Month 12`) are generated in the Timeline View showing `[Action Date] -> [Cutoff Date] -> [Delivery Date]` where every automatic delivery date falls on a Saturday.

2. **Manual Delivery Date Override & Ripple Toast**:
   - In the Timeline View, use the **Edit Delivery Date** picker on Cycle 1 to shift the delivery date forward (e.g., +4 days).
   - Verify the toast notification appears: `"Delivery shifted by 4 days. Subsequent 12 months successfully recalculated."`
   - Verify Cycles 2 through 12 updated their `Theoretical_Need_Date`, `Actual_Delivery_Date` (snapped backward to Saturday), `Order_Cutoff_Date`, and `Action_Date`.

3. **Operational Redlines (Stockout Risk, Lead Time Collision, Holiday Blackouts)**:
   - **Lead Time Collision**: Attempt to set a cycle's delivery date to tomorrow (less than 15 days from today). Confirm the edit is blocked with `"Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is [Date]."`
   - **Stockout Gap**: Override Cycle 2's delivery date so that `Delivery_Date(2) - Delivery_Date(1) = 30` days (`> 28`). Confirm Cycle 2 is flagged in **Orange** with `"Stockout Risk: Gap is 30 days, supply only lasts 28. Increase order volume or pull delivery forward."`
   - **Holiday Blackout**: Add a cycle's unadjusted `Action_Date` to **Holiday Blackout Dates** (`Blackout_Dates`). Confirm `Action_Date` automatically shifts backward before the holiday.
