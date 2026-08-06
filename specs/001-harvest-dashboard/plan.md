# Implementation Plan: harvest-dashboard

**Branch**: `001-harvest-dashboard` | **Date**: 2026-08-06 | **Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/001-harvest-dashboard/spec.md)

**Input**: Feature specification from `specs/001-harvest-dashboard/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Implement a read-only visual dashboard for administrators that displays aggregated lifetime harvest totals per product, and includes a tabbed interface to view precise monthly and yearly snapshot aggregates based on dropdown selections. The technical approach involves utilizing Spring Data JPA native `@Query` grouping to calculate aggregates within the database engine, and rendering the UI natively with Thymeleaf server-side templates.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 3.4.x (Web, Security, Data JPA), Thymeleaf, Lombok

**Storage**: H2 (local/test), PostgreSQL (production)

**Testing**: JUnit, Spring Boot Test

**Target Platform**: Server (Web application)

**Project Type**: Web application (Server-rendered HTML)

**Performance Goals**: <2 seconds load time for lifetime aggregates, <1s for monthly/yearly tabs (per Success Criteria).

**Constraints**: Must execute processing and aggregations natively within the database engine; MUST enforce security via `@PreAuthorize`.

**Scale/Scope**: Web dashboard UI serving internal admin/manager roles.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Financial Integrity)**: N/A - Dashboard is read-only.
- **Principle II (Inventory Traceability)**: PASS - Existing inventory lifecycle states are preserved. Dashboard merely reads them.
- **Principle III (Strict Security)**: PASS - Endpoints will enforce `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` and strict role authorization boundaries.
- **Principle IV (Defensive Concurrency)**: N/A - Dashboard is read-only, no locks or sequence generators are involved.
- **Principle V (Database-Driven Aggregation)**: PASS - Aggregations are strictly designed to run natively within the database using SQL `GROUP BY` via JPA `@Query` rather than in-memory iteration, strictly complying with the mandate.

## Project Structure

### Documentation (this feature)

```text
specs/001-harvest-dashboard/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (generated via /speckit.tasks)
```

### Source Code (repository root)

```text
# Option 1: Single project
src/
├── main/
│   ├── java/
│   │   └── .../
│   │       ├── controllers/
│   │       │   └── DashboardController.java
│   │       ├── dto/
│   │       │   └── ProductHarvestAggregateDto.java
│   │       ├── repositories/
│   │       │   └── InventoryUnitRepository.java (update with aggregate queries)
│   │       └── services/
│   │           └── DashboardService.java
│   └── resources/
│       └── templates/
│           └── dashboard/
│               └── harvest.html
tests/
└── java/
    └── .../
        └── controllers/
            └── DashboardControllerTest.java
```

**Structure Decision**: The feature follows the existing standard Spring Boot monolithic structure utilizing controllers, services, repositories, and Thymeleaf templates as outlined in the Constitution.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified. Feature strictly conforms to Constitution constraints.*
