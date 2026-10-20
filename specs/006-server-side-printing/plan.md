# Implementation Plan: Remote Server-Side Label Printing

**Branch**: `006-server-side-printing` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-server-side-printing/spec.md`

## Summary

Extend the batch label printing workflow to support remote server-side printing over a LAN print bridge (`H30PrintServer` at `http://192.168.1.53:8080` by default) when the application is accessed remotely (e.g., via a Cloudflare Tunnel), while preserving the existing client-side browser print button for local workstations. The backend will manage print server configuration via `application.properties` and `SettingsService` (`/admin/settings`), probe LAN print server reachability with a strict timeout (`<= 1500ms`), expose a `Server Print` button on `/batches/{id}/print` that is enabled only when connectivity is available, and generate a multi-page PDF document (using Apache PDFBox) that mathematically replicates the current FSSAI label layout, dimensions, 1mm safe margins, and auto-scaled bounding-box formatting before dispatching it to the LAN print server.

## Technical Context

**Language/Version**: Java 21 LTS, HTML5/CSS3/JS (Thymeleaf)

**Primary Dependencies**: Spring Boot 3.4.0 (Spring Web, Spring Security, Spring Data JPA, Thymeleaf), Apache PDFBox 3.0.3 (`org.apache.pdfbox:pdfbox`), ZXing 3.5.3, Java `HttpClient` / `Socket`

**Storage**: H2 (In-Memory for dev/test), PostgreSQL (Production) via existing `app_settings` table (`AppSetting` entity)

**Testing**: JUnit 5, Spring Boot Test (`MockMvc`, `@WithMockUser`), JDK `HttpServer` mock for LAN print server connectivity and payload verification

**Target Platform**: Linux server hosting Spring Boot web app communicating over LAN with Windows print workstation (`192.168.1.53:8080`)

**Project Type**: Monolithic Web Application (Spring Boot + Thymeleaf)

**Performance Goals**: Connectivity check `<= 1.5s` timeout (`SC-001`); multi-page PDF generation and LAN print dispatch `< 5s` (`SC-002`)

**Constraints**: Must enforce minimum 1mm hardware safe margin, aspect-ratio bounding-box auto-scaling, and crisp non-fractional border strokes in generated PDFs per Constitution Principle VI; must not block or degrade client-side printing when the LAN print server is offline.

**Scale/Scope**: Impacts `pom.xml`, `application*.properties`, `SettingsService`, `AdminController`, `HarvestBatchController`, a new `PrintServerService`, `admin/settings.html`, `batches/print.html`, and unit/integration tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Financial Integrity & Explicit Auditability**: **PASS** — Feature only reads batch/product metadata for label printing and manages system settings; no financial records or allocations are modified.
- **II. Complete Inventory Traceability & QR Lifecycle**: **PASS** — Does not alter `InventoryUnit` lifecycle states; supports printing full batches, single units (`unitId`), or sequence ranges (`seqStart`–`seqEnd`) identically to client-side printing.
- **III. Strict Security & Data Ownership**: **PASS** — `AdminController` (`/admin/settings`) will enforce `@PreAuthorize("hasRole('ADMIN')")` so only administrators can modify the print server URL (`FR-008`), and print endpoints require authenticated access.
- **IV. Defensive Concurrency & Data Consistency**: **PASS** — Label generation and HTTP dispatch are stateless per request and read-only with respect to domain entities.
- **V. Database-Driven Aggregation & Performance**: **PASS** — Uses existing repository queries (`unitRepository.findByBatchId(id)`).
- **VI. Physical Printing & Hardware Constraints**: **PASS** — Multi-page PDF generation in `PrintServerService` enforces the 1mm structural safe margin, 10mm Veg symbol placeholder with safe clearance, conditional nutrition table (`>= 75mm`), binary-search aspect-ratio bounding-box scaling, and integer/crisp stroke widths to avoid thermal print head clipping or vanished borders.

**Post-Design Gate Status**: **PASS** (All principles satisfied with zero violations).

## Project Structure

### Documentation (this feature)

```text
specs/006-server-side-printing/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── print-api.md     # REST & UI contracts for status check and server print
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/mushroom/stockkeeper/
│   │   ├── controller/
│   │   │   ├── AdminController.java          # Add @PreAuthorize("hasRole('ADMIN')") & default print server setting support
│   │   │   └── HarvestBatchController.java   # Add GET /batches/print-server/status & POST /batches/{id}/server-print
│   │   ├── dto/
│   │   │   ├── PrintServerStatusResponse.java # Connectivity status DTO
│   │   │   └── ServerPrintResponse.java      # Print job execution result DTO
│   │   └── service/
│   │       ├── SettingsService.java          # Add KEY_PRINT_SERVER_URL & property fallback
│   │       └── PrintServerService.java       # Connectivity check, multi-page PDF generator, & HTTP POST dispatcher
│   └── resources/
│       ├── application.properties            # Default app.print-server.* properties
│       ├── application-dev.properties        # Dev print server defaults
│       ├── application-prod.properties       # Prod print server defaults
│       └── templates/
│           ├── admin/settings.html           # Add Print Server URL input under Label Print Settings
│           └── batches/print.html            # Add connectivity-gated Server Print button & status feedback UI
└── test/
    └── java/com/mushroom/stockkeeper/
        ├── controller/
        │   └── HarvestBatchPrintControllerTest.java # Integration tests for status & server-print endpoints + Admin security
        └── service/
            └── PrintServerServiceTest.java          # Unit/integration tests for PDF layout replication & mock LAN server
```

**Structure Decision**: Extends the existing monolithic Spring Boot application structure under `src/main/java/com/mushroom/stockkeeper` and `src/main/resources/templates`.

## Complexity Tracking

No Constitution violations — table omitted.
