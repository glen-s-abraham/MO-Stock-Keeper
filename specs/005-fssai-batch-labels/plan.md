# Implementation Plan: FSSAI Compliant Batch Labels

**Branch**: `005-fssai-batch-labels` | **Date**: 2026-08-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-fssai-batch-labels/spec.md`

## Summary

Enhance the existing batch label printing template (`print.html`) to support FSSAI compliance by displaying required information such as FSSAI license number, Veg symbol placeholder, Registered Office Address, Customer Care Address, and conditionally including a Nutrition Information table for labels 75x75mm or larger. A database seed script will also be created to populate sample settings.

## Technical Context

**Language/Version**: Java 21, HTML/CSS (Thymeleaf)

**Primary Dependencies**: Spring Boot 3.4.x, Spring Data JPA

**Storage**: H2 (In-memory/Local), PostgreSQL (Production)

**Testing**: JUnit 5, MockMvc

**Target Platform**: Browser (print dialog)

**Project Type**: Web Application

**Performance Goals**: Label generation < 2s for 100 units

**Constraints**: HTML layout must cleanly fit dynamically sized thermal labels and standard paper formats without overflowing.

**Scale/Scope**: Impacts the existing `print.html` and `SettingsService`/`AppSetting` configurations.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Financial Integrity**: N/A (UI layout and read-only label generation).
- **Inventory Traceability**: QR code temporarily removed per FR-009; relying on printed Batch No and UUID for traceability.
- **Strict Security & Data Ownership**: Uses existing authenticated endpoints.
- **Defensive Concurrency**: Read-only display of batch/product data.
- **Database-Driven Aggregation**: N/A (No new aggregations required).

**Status**: PASS

## Project Structure

### Documentation (this feature)

```text
specs/005-fssai-batch-labels/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # To be created
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/mushroom/stockkeeper/
│   │   ├── service/SettingsService.java (Update for new settings)
│   │   ├── config/DataSeeder.java (Add seed data for FSSAI compliance)
│   │   └── controller/HarvestBatchController.java (Pass new settings to model)
│   └── resources/
│       └── templates/
│           └── batches/
│               └── print.html (Update layout)
└── test/
    └── java/com/mushroom/stockkeeper/
        └── controller/HarvestBatchControllerTest.java (Update tests)
```

**Structure Decision**: Modifying the existing monolithic Spring Boot application structure.

## Complexity Tracking

N/A
