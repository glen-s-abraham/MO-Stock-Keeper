# Phase 1 Interface Contracts: Adaptive Rolling Procurement Scheduler (ARPS)

**Feature**: `008-adaptive-procurement-scheduler`
**Date**: 2026-11-13

## Web & REST Endpoints (`ProcurementController`)

### 1. `GET /procurement`
Renders the Adaptive Rolling Procurement Scheduler main page containing:
- **Hub View**: Current month's active schedules and all active schedules with `Action_Date` status badge (`OVERDUE` in Red, `UPCOMING` in Yellow, `SCHEDULED` in Green/Neutral).
- **Timeline View**: 12-month cascading timeline (`[Action Date] -> [Cutoff Date] -> [Delivery Date]`) for the selected schedule (`?scheduleId={id}`, defaulting to the first active schedule).
- **New Schedule Modal** & **Holiday Blackout Dates Modal**.

### 2. `POST /procurement/schedules`
Creates a new `ProcurementSchedule` and generates its 12-cycle projection.
- **Form Parameters**:
  - `scheduleName` (`String`, required)
  - `supplyLastingDays` (`Integer`, required, `>= 7`)
  - `vendorLeadTime` (`Integer`, required, `>= 1`)
  - `actionBuffer` (`Integer`, required, `>= 0`)
  - `preferredDeliveryDay` (`Integer`, required, `0..6`)
  - `initialDeliveryDate` (`LocalDate`, optional; defaults to earliest `preferredDeliveryDay` on or after `today + vendorLeadTime + actionBuffer`)
- **Success Response**: Redirects to `/procurement?scheduleId={newId}` with flash toast message.
- **Validation Error Response**: Redirects to `/procurement` with flash `error` message detailing the validation or lead-time violation.

### 3. `POST /procurement/schedules/{scheduleId}/override`
Overrides `Actual_Delivery_Date(cycleIndex)` and cascades recalculation through Cycle 12.
- **Path Variable**: `scheduleId` (`Long`)
- **Form / Query Parameters**:
  - `cycleIndex` (`Integer`, `1..12`)
  - `newDeliveryDate` (`LocalDate`, `YYYY-MM-DD`)
- **Success Behavior**:
  - Updates Cycle `cycleIndex` and recalculates Cycles `cycleIndex + 1` through `12`.
  - Sets flash attribute `toastMessage`: `"Delivery shifted by X days. Subsequent 12 months successfully recalculated."`
  - Redirects to `/procurement?scheduleId={scheduleId}`.
- **Error Behavior (Lead Time Collision)**:
  - Blocks edit, sets flash attribute `error`: `"Invalid Date: Vendor requires {vendorLeadTime} days lead time. Earliest possible delivery is {earliestDate}."`
  - Redirects to `/procurement?scheduleId={scheduleId}`.

### 4. `POST /procurement/api/schedules/{scheduleId}/override` (JSON AJAX Endpoint)
Supports instant in-page calendar picker override with JSON response for seamless UI toast and timeline refresh.
- **Request Body / Params**: `cycleIndex` (`Integer`), `newDeliveryDate` (`LocalDate`)
- **200 OK JSON**:
  ```json
  {
    "success": true,
    "shiftedDays": 4,
    "message": "Delivery shifted by 4 days. Subsequent 12 months successfully recalculated.",
    "scheduleId": 1
  }
  ```
- **400 Bad Request JSON (Lead Time Collision)**:
  ```json
  {
    "success": false,
    "error": "Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is 2026-11-28."
  }
  ```

### 5. `POST /procurement/blackout-dates`
Updates the global `Blackout_Dates` setting (`procurement_blackout_dates`) and recalculates active schedules' `Action_Date` values.
- **Form Parameter**: `blackoutDates` (`String` of comma/newline-separated `YYYY-MM-DD` dates)
- **Response**: Redirects to `/procurement` with flash confirmation message.

### 6. `POST /procurement/schedules/{scheduleId}/delete`
Soft-deactivates (`active = false`) a procurement schedule from the active Hub View (preserving audit history per Constitution Principle I).
