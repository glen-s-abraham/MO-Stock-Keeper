# Implementation Plan: Adaptive Rolling Procurement Scheduler (ARPS)

**Branch**: `[008-adaptive-procurement-scheduler]` | **Date**: 2026-11-13 | **Spec**: [spec.md](file:///home/glen-personal/projects/MO-Stock-Keeper/specs/008-adaptive-procurement-scheduler/spec.md)

**Input**: Feature specification from `/specs/008-adaptive-procurement-scheduler/spec.md`

## Summary

Implement the **Adaptive Rolling Procurement Scheduler (ARPS)** with a dedicated top-level `Procurement` navigation tab (`/procurement`). Users can create procurement schedules (`Schedule_Name`, `Supply_Lasting_Days`, `Vendor_Lead_Time`, `Action_Buffer`, `Preferred_Delivery_Day`), monitor current month active schedules in the **Hub View** with Yellow (Upcoming) and Red (Overdue) `Action_Date` alerts, inspect the **12-Month Timeline View** (`[Action Date] -> [Cutoff Date] -> [Delivery Date]`), and manually override any cycle's delivery date with cascading "ripple" recalculations, toast notifications, **Stockout Gap** Orange warnings, **Lead Time Collision** blocking, and global **Holiday Blackout** adjustments.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**: Spring Boot 3.4.x (Spring Web, Spring Data JPA, Spring Security, Spring Validation), Thymeleaf + Layout Dialect, Lombok, Bootstrap 5.3

**Storage**: H2 (In-Memory for dev/test), PostgreSQL (production) via Spring Data JPA (`sk_procurement_schedules`, `sk_procurement_cycle_nodes`, and `sk_app_settings`)

**Testing**: JUnit 5, Spring Boot Test (`MockMvc`, `@DataJpaTest` / `@SpringBootTest`), Mockito

**Target Platform**: Linux / JVM Web Application

**Project Type**: Web Application (Spring Boot MVC + Thymeleaf)

**Performance Goals**: `< 50ms` for 12-cycle cascading ripple recalculation and persistence

**Constraints**: Must enforce optimistic locking (`@Version`), soft deletion (`active = false`), and authenticated access control

**Scale/Scope**: Dozens of active procurement schedules, 12 cycle nodes per schedule, global holiday blackout calendar list

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Financial Integrity & Explicit Auditability**: PASS — Procurement schedules use soft deactivation (`active = false`) rather than hard deletion, and do not mutate financial/invoice records.
- **II. Complete Inventory Traceability & QR Lifecycle**: PASS — ARPS is a forward-looking procurement planning scheduler and does not create phantom `InventoryUnit` records or bypass QR states.
- **III. Strict Security & Data Ownership**: PASS — All `/procurement/**` endpoints require authentication via Spring Security, with CSRF protection enforced on all POST operations.
- **IV. Defensive Concurrency & Data Consistency**: PASS — `ProcurementSchedule` includes `@Version` optimistic locking to prevent lost updates on concurrent schedule overrides.
- **V. Database-Driven Aggregation & Performance**: PASS — Repository queries filter active schedules directly in SQL (`findByActiveTrueOrderByCreatedAtDesc()`).
- **VI. Physical Printing & Hardware Constraints**: PASS (N/A) — Feature does not modify physical thermal label rendering.

## Project Structure

### Documentation (this feature)

```text
specs/008-adaptive-procurement-scheduler/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── http-endpoints.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
src/main/java/com/mushroom/stockkeeper/
├── controller/
│   ├── ProcurementController.java
│   └── AdminController.java
├── model/
│   ├── ProcurementSchedule.java
│   └── ProcurementCycleNode.java
├── repository/
│   ├── ProcurementScheduleRepository.java
│   └── ProcurementCycleNodeRepository.java
└── service/
    ├── ProcurementSchedulerService.java
    └── SettingsService.java

src/main/resources/templates/
├── layout/
│   └── base.html
├── admin/
│   └── settings.html
└── procurement/
    └── index.html

src/test/java/com/mushroom/stockkeeper/
├── service/
│   └── ProcurementSchedulerServiceTest.java
└── controller/
    └── ProcurementControllerTest.java
```

**Structure Decision**: Standard single-project Spring Boot layered architecture matching the existing `com.mushroom.stockkeeper` package hierarchy (`model`, `repository`, `service`, `controller`) and Thymeleaf templates under `src/main/resources/templates/procurement/`.

## Complexity Tracking

No constitution violations; complexity tracking not required.
