# Phase 0: Research & Technical Decisions

## 1. Database-Driven Aggregation Strategy
- **Context**: The Visual Dashboard requires lifetime, monthly, and yearly aggregated totals of harvested products. Constitution Principle V strictly mandates that data processing and aggregations MUST be executed natively within the database engine, rather than through in-memory entity iteration.
- **Decision**: Use Spring Data JPA `@Query` with JPQL and `GROUP BY` clauses projecting directly into Data Transfer Objects (DTOs), or native SQL queries if complex date functions are required (e.g., extracting month/year from timestamps across different database engines like H2 vs PostgreSQL).
- **Rationale**: Projects directly from the database to DTOs without hydrating full JPA entities. This guarantees optimal performance, minimizes memory footprint, and strictly complies with the system's Constitution. For monthly/yearly grouping, database-specific functions (like `EXTRACT(MONTH FROM date)`) might be needed, or we can use native queries specifically tailored for the target databases.
- **Alternatives considered**: 
  - Fetching all `InventoryUnit` or `Harvest` records into memory and using Java Streams to group and sum (Strictly rejected due to Constitution Principle V).
  - Using a materialized view (Overly complex for the current scale, though a viable future optimization if performance degrades on very large PostgreSQL deployments).

## 2. Web UI Components & Interactivity
- **Context**: The dashboard requires tabbed navigation (Monthly vs Yearly) and dropdowns for dynamic data fetching. The project uses Thymeleaf for server-side rendering.
- **Decision**: Use standard Thymeleaf templates combined with vanilla JavaScript (or lightweight HTMX/Alpine.js if already present in the project) to handle tab switching and dynamic dropdown submissions via asynchronous fetches (AJAX) or simple form GET requests.
- **Rationale**: Keeps the implementation aligned with the existing stack (Spring Web + Thymeleaf). Simple GET requests with query parameters (e.g., `?tab=monthly&month=08&year=2026`) are straightforward to implement, cacheable, and require no heavy frontend framework.
- **Alternatives considered**: 
  - Introducing React/Vue for the dashboard (Rejected as it violates the existing project architecture of server-side rendered Thymeleaf).

## 3. Security and Authorization
- **Context**: The dashboard aggregates critical business data.
- **Decision**: The dashboard controller and all related data endpoints must be secured using Spring Security, specifically requiring `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` (or the equivalent management role existing in the system).
- **Rationale**: Constitution Principle III requires strict security and access controls at the controller layer.
- **Alternatives considered**: N/A, securing business data is mandatory.
