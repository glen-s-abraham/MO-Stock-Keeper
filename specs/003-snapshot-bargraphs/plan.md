# Implementation Plan: snapshot-bargraphs

**Branch**: `003-snapshot-bargraphs` | **Date**: 2026-08-06 | **Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/003-snapshot-bargraphs/spec.md)

**Input**: Feature specification from `specs/003-snapshot-bargraphs/spec.md`

## Summary

Enhance the Harvest Dashboard by visualizing the data using bar graphs via Chart.js. The Monthly snapshot will feature a toggleable Daily/Weekly chart, and the Yearly snapshot will feature a Monthly chart. Data will be fetched asynchronously via a new REST endpoint to allow seamless toggling without page reloads.

## Technical Context

**Language/Version**: Java 21 LTS, JavaScript (ES6)

**Primary Dependencies**: Spring Boot 3.4.x, Thymeleaf, Chart.js (CDN)

**Storage**: H2 / PostgreSQL

**Testing**: JUnit, Spring Boot Test

**Target Platform**: Server (Web application) / Client (Browser)

**Project Type**: Web application

**Performance Goals**: <500ms overhead for rendering the charts.

**Constraints**: Must strictly adhere to Constitution Principle V (all aggregation happens in the DB, not in Java Streams).

**Scale/Scope**: Medium. Requires new SQL aggregations, DTOs, a REST endpoint, and frontend JS logic.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I-IV**: N/A - Standard feature addition.
- **Principle V (Database-Driven Aggregation)**: PASS - The design explicitly calls for Native SQL/JPQL queries to handle the time-series grouping (by day, week, month). No Java-side grouping will be implemented.

## Project Structure

### Documentation (this feature)

```text
specs/003-snapshot-bargraphs/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (generated via /speckit.tasks)
```

### Source Code

```text
src/
└── main/
    ├── java/
    │   └── com/mushroom/stockkeeper/
    │       ├── dto/
    │       │   ├── ChartDatasetDto.java
    │       │   ├── ChartResponseDto.java
    │       │   └── TimeSeriesAggregateDto.java
    │       ├── repository/
    │       │   └── InventoryUnitRepository.java (Native time-series queries)
    │       ├── service/
    │       │   └── DashboardService.java (Chart formatting logic)
    │       └── controller/
    │           └── DashboardController.java (New @ResponseBody endpoint)
    └── resources/
        └── templates/
            └── dashboard/
                └── harvest.html (Canvas elements and JS integration)
```

## Strategy

1. **Database & Data Transfer**: Create the DTOs and implement the native SQL grouping queries.
2. **Service Layer**: Implement the logic to transform flat database results into the structured `ChartResponseDto`.
3. **API Endpoint**: Expose the data securely via a new GET endpoint.
4. **Frontend Integration**: Import Chart.js, add the canvas elements, and write the fetch/render logic in Thymeleaf/JS.
