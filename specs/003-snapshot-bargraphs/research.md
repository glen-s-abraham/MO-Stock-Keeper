# Phase 0: Research & Technical Decisions

## 1. Charting Library
- **Context**: We need to render bar graphs in the dashboard.
- **Decision**: Use **Chart.js** included via CDN.
- **Rationale**: Chart.js is an industry-standard, lightweight, canvas-based charting library. It requires zero build-step overhead for vanilla HTML/JS projects and integrates perfectly into Thymeleaf templates. It natively supports grouped and stacked bar charts for multiple datasets (products).
- **Alternatives considered**: D3.js (too complex for simple bar charts), Google Charts (heavier dependency), ApexCharts (good, but Chart.js has simpler out-of-the-box styling).

## 2. Data Fetching Strategy (Toggle Without Reload)
- **Context**: FR-004 requires switching between Daily and Weekly distributions in the Monthly snapshot without a full page reload.
- **Decision**: Create a dedicated `@ResponseBody` REST endpoint in `DashboardController` (e.g., `/dashboard/harvest/chart-data`) that returns the aggregated data in JSON format. The frontend will use `fetch()` to retrieve this data and update the Chart.js instance.
- **Rationale**: Clean separation of concerns. Thymeleaf handles the initial layout and tabular data, while JS handles dynamic chart updates.

## 3. Database Aggregation Constraints (Principle V)
- **Context**: The project constitution mandates that all aggregation occurs in the database using `GROUP BY`, forbidding Java-side stream grouping. We need to group by Day, Week, and Month.
- **Decision**: We implemented standard JPQL queries using Hibernate 6's native support for the `EXTRACT(DAY|WEEK|MONTH FROM date)` function in `InventoryUnitRepository`.
- **Rationale**: `EXTRACT()` is the SQL-standard way to retrieve date parts. By using it in JPQL without any string casting, Hibernate perfectly translates the query into compatible SQL for both our H2 testing environment and the production PostgreSQL database.
