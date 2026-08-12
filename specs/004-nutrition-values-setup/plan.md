# Implementation Plan: Nutrition Values Setup

**Branch**: `[004-nutrition-values-setup]` | **Date**: 2026-08-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-nutrition-values-setup/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

This feature extends the product catalog to support configuring and displaying nutritional values. It introduces a toggle to enable nutrition data, a configurable base unit (e.g., 100g, 100ml), and a dynamic tabular setup where users can manually add, edit, and remove individual nutritional components (like Calories, Protein, etc.) without page reloads. The data is formatted for both digital view and physical label printing.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 3.4.x, Thymeleaf, Spring Data JPA, Lombok

**Storage**: PostgreSQL (production) / H2 (development)

**Testing**: JUnit 5, Spring Boot Test

**Target Platform**: Server-Side Rendered Web Application (JVM / Browser)

**Project Type**: Web Application

**Performance Goals**: Support typical CRUD latency for product modifications.

**Constraints**: Soft-deletion for database records, optimistic locking (`@Version`) for entities to prevent concurrent modification races.

**Scale/Scope**: Impacts Product entity and introduces a related NutritionLineItem table. Minimal scaling impact, standard relational data scaling applies.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Financial Integrity & Explicit Auditability**: Entities use soft deletion (`isHidden = true`). Passed.
- **II. Complete Inventory Traceability & QR Lifecycle**: N/A for this feature directly, but extends the product definition. Passed.
- **III. Strict Security & Data Ownership**: Role-based access for editing products will be maintained. Passed.
- **IV. Defensive Concurrency & Data Consistency**: Optimistic locking (`@Version`) is mandated for the new `NutritionLineItem` entity. Passed.
- **V. Database-Driven Aggregation & Performance**: Native SQL aggregations are not strictly needed for line-item retrieval, standard JPA relations suffice. Passed.

## Project Structure

### Documentation (this feature)

```text
specs/004-nutrition-values-setup/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (future)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── [package_root]/
│   │       ├── domain/
│   │       ├── repository/
│   │       ├── service/
│   │       └── web/
│   └── resources/
│       ├── templates/
│       └── static/
└── test/
    └── java/
        └── [package_root]/
```

**Structure Decision**: The feature integrates into the existing Spring Boot application architecture. The new `NutritionLineItem` entity belongs in the `domain` package, while Thymeleaf templates in `src/main/resources/templates/` will be updated for the UI. JavaScript for dynamic row addition will reside in `src/main/resources/static/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations.*
