# API & UI Contracts: Remote Server-Side Label Printing

**Feature**: `006-server-side-printing`
**Date**: 2026-10-20

## 1. Check Print Server Connectivity Status

- **Endpoint**: `GET /batches/print-server/status`
- **Authorization**: Authenticated user (any role permitted to view/print batches)
- **Purpose**: Probes connectivity to the configured LAN print server within a strict timeout (`<= 1500ms`) and returns availability state to enable/disable the `Server Print` button on the batch print page.
- **Response (`200 OK` - `application/json`)**:
  ```json
  {
    "available": true,
    "printServerUrl": "http://192.168.1.53:8080",
    "message": "Print server is online"
  }
  ```
  When offline/unreachable:
  ```json
  {
    "available": false,
    "printServerUrl": "http://192.168.1.53:8080",
    "message": "Print server unreachable (Connection timed out)"
  }
  ```

---

## 2. Trigger Server-Side Batch Label Print

- **Endpoint**: `POST /batches/{id}/server-print`
- **Authorization**: Authenticated user
- **Query / Form Parameters** (optional, matching `GET /batches/{id}/print` filter parameters):
  - `unitId` (`Long`, optional): Print only a single specific `InventoryUnit`.
  - `seqStart` (`Integer`, optional): Start sequence number for range printing.
  - `seqEnd` (`Integer`, optional): End sequence number for range printing.
- **Behavior**:
  1. Verifies the batch exists and resolves the target `InventoryUnit` list using the exact same filtering logic as `GET /batches/{id}/print`.
  2. Verifies print server reachability.
  3. Generates the multi-page PDF document replicating the current label sheet format (`THERMAL_CUSTOM`, `THERMAL_4x6`, `THERMAL_2x1`, `A4_24`, `A4_40`), FSSAI compliance fields, 1mm safe margins, and dynamic bounding-box auto-scaling.
  4. Sends the PDF payload via HTTP `POST` to `{printServerUrl}/print` with `Content-Type: application/pdf`.
- **Success Response (`200 OK` - `application/json`)**:
  ```json
  {
    "success": true,
    "batchId": 1,
    "unitsPrinted": 25,
    "bytesSent": 18432,
    "message": "Successfully sent 25 label(s) to print server."
  }
  ```
- **Error Response (`503 Service Unavailable` or `400 Bad Request` - `application/json`)**:
  ```json
  {
    "success": false,
    "batchId": 1,
    "unitsPrinted": 0,
    "bytesSent": 0,
    "message": "Print server is offline or unreachable at http://192.168.1.53:8080"
  }
  ```

---

## 3. Outbound Contract to LAN Print Server (`H30PrintServer`)

- **Target URL**: `{printServerUrl}/print` (default `http://192.168.1.53:8080/print`; if `printServerUrl` already ends with `/print` or another path, uses it directly or appends `/print` when only base host:port is configured)
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/pdf`
  - `X-Print-Format: PDF`
  - `X-Label-Sheet-Size: {labelSheetSize}`
  - `X-Label-Width-Mm: {widthMm}`
  - `X-Label-Height-Mm: {heightMm}`
  - `X-Label-Count: {unitCount}`
- **Body**: Raw binary bytes of the generated multi-page PDF document.

---

## 4. UI Contracts

### Batch Print View (`src/main/resources/templates/batches/print.html`)
- Retains existing client-side button:
  - `<button onclick="window.print()" class="btn btn-primary">Print</button>`
- Adds server-side print button and status badge in the `.no-print` toolbar:
  - `<button id="serverPrintBtn" ... class="btn btn-success" th:disabled="${!printServerAvailable}">Server Print</button>`
  - Status indicator showing whether the LAN print server is connected or offline.
  - On page load, queries `GET /batches/print-server/status` to ensure fresh connectivity state and on click sends `POST /batches/{id}/server-print` (including CSRF token and active `unitId`/`seqStart`/`seqEnd` parameters), disabling the button while printing and displaying an alert banner with the result.

### System Settings View (`src/main/resources/templates/admin/settings.html`)
- Under **Label Print Settings**, adds input field:
  - `name="print_server_url"` (Label: `Print Server URL`, placeholder: `http://192.168.1.53:8080`)
- Protected by `@PreAuthorize("hasRole('ADMIN')")` on `AdminController`.
