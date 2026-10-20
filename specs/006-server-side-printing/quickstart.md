# Quickstart Validation Guide: Remote Server-Side Label Printing

**Feature**: `006-server-side-printing`
**Date**: 2026-10-20

## Prerequisites

- Java 21 LTS and Maven installed (`./mvnw` wrapper included in repository)
- Optional for live hardware test: Windows workstation on LAN (`192.168.1.53:8080`) running `H30PrintServer` with `Run-Admin-Setup.bat` applied

## 1. Run Automated Unit & Integration Tests

Verify connectivity probing, multi-page PDF generation, controller endpoints, and admin role security using the test suite (which spins up a local mock HTTP server to simulate `H30PrintServer` both online and offline):

```bash
./mvnw test -Dtest=PrintServerServiceTest,HarvestBatchPrintControllerTest,AdminSettingsSecurityTest
```

**Expected Outcome**:
- `PrintServerServiceTest` verifies multi-page PDF generation for `THERMAL_CUSTOM` (e.g., 75x75mm with nutrition table and 50x50mm without nutrition table) and `A4_40`, verifies connectivity checks return `true` when the mock server is listening and `false` within `1.5s` when offline, and confirms binary PDF dispatch to `/print`.
- `HarvestBatchPrintControllerTest` verifies `GET /batches/print-server/status`, `GET /batches/{id}/print` button state rendering, and `POST /batches/{id}/server-print` with full batch, `unitId`, and `seqStart`/`seqEnd` filters.
- `AdminSettingsSecurityTest` verifies that only `ROLE_ADMIN` users can view or update `print_server_url` in `/admin/settings`.

## 2. Manual End-to-End Verification

### Scenario A: Configure Print Server URL
1. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```
2. Log in as an Administrator and navigate to **System Settings** (`/admin/settings`).
3. Locate the **Label Print Settings** section and verify the **Print Server URL** field defaults to `http://192.168.1.53:8080`.
4. Save settings and confirm the success message appears.

### Scenario B: Verify Connectivity-Gated Button State (Offline vs. Online)
1. Navigate to any Harvest Batch (`/batches/{id}`) and click **Print Labels** (`/batches/{id}/print`).
2. **When `H30PrintServer` (`192.168.1.53:8080`) is offline/unreachable**:
   - The **Server Print** button is disabled (`disabled` attribute set) with an offline indicator.
   - The client-side **Print** button remains enabled and opens the browser print dialog when clicked.
3. **When `H30PrintServer` (`192.168.1.53:8080`) is online**:
   - The **Server Print** button is enabled.
   - Clicking **Server Print** generates the multi-page PDF matching the batch labels on screen, POSTs it to `http://192.168.1.53:8080/print`, and displays a success notification banner showing the number of labels printed.
