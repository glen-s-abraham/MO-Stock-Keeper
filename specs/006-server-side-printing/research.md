# Research: Remote Server-Side Label Printing

**Feature**: `006-server-side-printing`
**Date**: 2026-10-20

## 1. Multi-Page PDF Label Replication Strategy

- **Decision**: Support two complementary PDF generation pathways for server-side printing:
  1. **Backend Native Multi-Page PDF Generation (Apache PDFBox 3.x)**: A dedicated `PrintServerService` in the Spring Boot backend generates a multi-page PDF document (1 label per page for `THERMAL_CUSTOM`, `THERMAL_4x6`, `THERMAL_2x1`, or grid pages for `A4_24`/`A4_40`) that mathematically replicates the exact FSSAI layout, 1mm safe margins, 10mm Veg symbol placeholder, company/batch details, conditional nutrition table (`>= 75mm`), and binary-search aspect-ratio auto-scaling defined in Principle VI.
  2. **Client-Assisted Exact DOM PDF / Print Payload Support**: Optionally allow the `/batches/{id}/server-print` endpoint to accept either a direct server-rendered trigger (default JSON/form POST with `unitId`, `seqStart`, `seqEnd`) or a pre-rendered PDF byte payload if captured from the browser DOM, ensuring 100% reliability whether triggered via lightweight API call or rich browser view.
- **Rationale**:
  - Generating the multi-page PDF directly on the Java backend using Apache PDFBox (`org.apache.pdfbox:pdfbox:3.0.3`) has zero external OS browser dependencies (no headless Chromium/Playwright overhead on the Linux server), executes in `< 200ms` for a batch of labels, and works deterministically in headless tests (`MockMvc`) and remote mobile sessions over Cloudflare Tunnel.
  - Replicating the exact page dimensions (`width` mm x `height` mm converted to PDF points at `72 / 25.4` pt/mm) and bounding-box scaling in `PrintServerService` guarantees compliance with Constitution Principle VI (minimum 1mm padding, binary-search aspect-ratio scaling, crisp vector/monochrome strokes without sub-pixel anti-aliasing).
- **Alternatives Considered**:
  - *Headless Chromium / Puppeteer on Linux server*: Rejected because installing and maintaining a headless browser binary inside the Java server environment adds heavy memory/deployment overhead and slows down individual print requests.
  - *Raw TSPL string concatenation only*: Rejected because the user specifically requested replicating the current multi-page PDF print setup and the `H30C-lite` printer uses a proprietary Windows GDI/raster driver.

## 2. LAN Print Server Communication & Connectivity Probing (`H30PrintServer`)

- **Decision**: Use Java 21's built-in `java.net.http.HttpClient` (and `java.net.Socket` fallback for reachability) within `PrintServerService` to check connectivity and dispatch print jobs to the configured print server URL (default `http://192.168.1.53:8080`).
- **Rationale**:
  - **Connectivity Check (`FR-003`, `FR-004`)**: Opening a TCP socket connection (`Socket.connect(InetSocketAddress, timeoutMs)`) or issuing a lightweight HTTP `GET /health` / `HEAD /` probe to the parsed host and port from `print_server_url` with a strict `1500ms` timeout accurately verifies whether `H30PrintServer` on `192.168.1.53:8080` is online and reachable through the Windows Firewall without sending dummy print bytes to the spooler.
  - **Print Job Dispatch (`FR-005`, `FR-006`)**: Sends an HTTP `POST` request to `{printServerUrl}/print` (or configured path) with `Content-Type: application/pdf` (and `X-Print-Format: PDF`, `X-Label-Width-Mm`, `X-Label-Height-Mm` headers) containing the generated multi-page PDF binary payload (`--data-binary`), with a 5-second request timeout.
  - **Non-Blocking Page Load (`SC-001`)**: The batch print page (`GET /batches/{id}/print`) both injects the initial server print configuration state and asynchronously invokes `GET /batches/print-server/status` on page load to dynamically enable/disable the `Server Print` button and display live connectivity status without delaying initial HTML rendering if the LAN host is unreachable.
- **Alternatives Considered**:
  - *Synchronous blocking check only during `GET /batches/{id}/print`*: If the LAN IP is offline and drops packets, every print page load would stall for 2 seconds before rendering the HTML. Combining fast backend timeout (`1500ms`) with an asynchronous status endpoint (`GET /batches/print-server/status`) keeps page load instantaneous while fulfilling both server-side and dynamic UI verification.

## 3. Print Server Configuration Storage & Fallback Hierarchy

- **Decision**: Manage the print server configuration via `SettingsService` (`AppSetting` key `print_server_url`) backed by an `@Value("${app.print-server.url:http://192.168.1.53:8080}")` property fallback in `application.properties` (`application-dev.properties` and `application-prod.properties`).
- **Rationale**:
  - Provides out-of-the-box readiness (`http://192.168.1.53:8080`) via Spring `application.properties` before an admin ever touches the database settings.
  - Allows administrators to override the URL/IP/port at runtime via `/admin/settings` (`admin/settings.html`) under the "Label Print Settings" section without restarting the Spring Boot server (`SC-004`).
  - Enforces `@PreAuthorize("hasRole('ADMIN')")` on `AdminController` to comply with Constitution Principle III (Strict Security & Data Ownership).
- **Alternatives Considered**:
  - *Static `application.properties` only*: Rejected because changing the workstation IP or port would require rebuilding/restarting the application.
  - *Database `AppSetting` only without property default*: Rejected because the user requested adding the print server config to the application properties as well.
