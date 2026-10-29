# Implementation Plan: Manual Label Printer

**Branch**: `[007-manual-label-printer]` | **Date**: 2026-10-29 | **Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/007-manual-label-printer/spec.md)

**Input**: Feature specification from `/specs/007-manual-label-printer/spec.md`

## Summary

Add a dedicated **Manual Label Printer** section (`/batches/manual-labels`) that enables operators to pre-print emergency FSSAI-compliant product stickers for use during power or internet outages. Each generated sticker receives its own sequential batch code (`M-0001`, `M-0002`, ...) from a persisted sequence counter (`AppSetting` key `manual_label_last_sequence`), while replacing `Pkd:` and `Exp:` date values with write-in blank placeholders (`____________`). The feature reuses `templates/batches/print.html` and `PrintServerService` so layout, nutrition table rules, auto-scaling, client-side printing, and LAN Server Print remain 100% consistent with online batch labels, without creating phantom `HarvestBatch` or `InventoryUnit` records in live inventory.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 3.4.x (Spring Web, Spring Data JPA, Spring Security), Thymeleaf + Layout Dialect, Apache PDFBox 3.x, Headless Chrome / PrintServer PDF bridge

**Storage**: H2 (dev/test) & PostgreSQL (prod) via existing `app_settings` table (`AppSettingRepository`)

**Testing**: JUnit 5, Spring Boot Test (`MockMvc`, `@SpringBootTest`), Mockito

**Target Platform**: Linux application server + Web browser (desktop/mobile) + optional Windows LAN Print Server (`H30PrintServer`)

**Project Type**: Web application (Spring Boot MVC + Thymeleaf server-side templates)

**Performance Goals**: Manual label generation and print preview rendering for up to 500 labels in < 2 seconds

**Constraints**: Must not create `HarvestBatch` or `InventoryUnit` database records; must preserve exact FSSAI layout, 1mm hardware safe margin, and `autoFitLabels()` scaling; sequence allocation must be concurrency-safe

**Scale/Scope**: 1–500 manual labels per print run; global monotonic sequence formatted as `M-%04d` (`M-0001` through `M-9999` and `M-10000+`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Financial Integrity & Explicit Auditability)**: PASS — Manual label pre-printing does not touch financial records, invoices, or payments. Sequence allocations and sequence adjustments are logged via `AuditService`.
- **Principle II (Complete Inventory Traceability & QR Lifecycle)**: PASS — Pre-printed emergency labels deliberately do not create phantom `InventoryUnit` or `HarvestBatch` records in the database before actual harvest/packing occurs.
- **Principle III (Strict Security & Data Ownership)**: PASS — Endpoints under `/batches/manual-labels` are protected by the existing Spring Security filter chain alongside batch management endpoints.
- **Principle IV (Defensive Concurrency & Data Consistency)**: PASS — Sequence allocation (`allocateManualLabelSequence`) is synchronized and transactional so concurrent requests cannot receive overlapping `M-XXXX` ranges, and uses the Post-Redirect-Get (PRG) pattern so page refreshes or Server Print calls never double-increment the sequence.
- **Principle V (Database-Driven Aggregation & Performance)**: PASS — Uses single-row key lookup on `app_settings` (`manual_label_last_sequence`) and in-memory transient models for rendering print pages.
- **Principle VI (Physical Printing & Hardware Constraints)**: PASS — Directly reuses `batches/print.html` and `PrintServerService` (including 1mm minimum safe margins, `autoFitLabels()` binary-search aspect-ratio scaling, and 10mm vegetarian symbol placeholder).

## Project Structure

### Documentation (this feature)

```text
specs/007-manual-label-printer/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── manual-label-endpoints.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
src/main/java/com/mushroom/stockkeeper/
├── controller/
│   └── HarvestBatchController.java       # Manual label form, PRG sequence allocation, print view, server-print endpoint
├── dto/
│   └── ManualLabelRange.java             # Value object holding allocated startSeq, endSeq, and formatted codes
├── service/
│   ├── SettingsService.java              # Atomic sequence counter read/allocate/update (manual_label_last_sequence)
│   └── PrintServerService.java           # Support per-unit M-XXXX batch code & blank Pkd/Exp placeholders in HTML & PDFBox
└── model/
    └── AppSetting.java                   # Existing entity storing manual_label_last_sequence

src/main/resources/templates/
├── layout/
│   └── base.html                         # Navigation link to Manual Labels
└── batches/
    ├── list.html                         # Action button to open Manual Labels section
    ├── manual-labels.html                # New Manual Label Printer form (new set, reprint range, sequence adjustment)
    └── print.html                        # Updated to display per-unit M-XXXX code and write-in Pkd/Exp placeholders in manual mode

src/test/java/com/mushroom/stockkeeper/
├── controller/
│   └── ManualLabelPrintControllerTest.java # Integration/controller tests for manual label flows
└── service/
    └── PrintServerServiceTest.java       # Unit tests verifying manual label PDF/HTML rendering
```

**Structure Decision**: Single Spring Boot web application structure matching existing conventions under `com.mushroom.stockkeeper` and `src/main/resources/templates/batches/`.

## Complexity Tracking

No Constitution violations identified.
