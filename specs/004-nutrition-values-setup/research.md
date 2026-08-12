# Phase 0: Research & Technical Decisions

## Technical Context Resolution

The technical context for MO-Stock-Keeper is clearly defined in the project constitution (`constitution.md`), so no external research is required.

- **Decision 1: Tech Stack Alignment**
  - **Decision**: Use Java 21, Spring Boot 3.4.x, Spring Data JPA, and Thymeleaf.
  - **Rationale**: Mandated by the project architecture in the constitution.
  - **Alternatives considered**: None, adhering to existing stack.

- **Decision 2: Data Persistence Strategy**
  - **Decision**: Use a relational mapping (JPA/Hibernate) for the nutrition tabular setup, linking a new `NutritionLineItem` entity to the existing `Product` entity with a One-to-Many relationship. Product will have a new boolean flag and base unit configuration.
  - **Rationale**: Standard Spring Data JPA pattern. Easy to query and fits existing entity structure.
  - **Alternatives considered**: Storing tabular data as a JSON column (rejected because H2/PostgreSQL compatibility issues might arise, and JPA entity relations are safer for future schema migrations and auditability).

- **Decision 3: Concurrency Control**
  - **Decision**: Apply `@Version` optimistic locking to the `NutritionLineItem` or rely on the parent `Product` optimistic lock if cascaded.
  - **Rationale**: Principle IV (Defensive Concurrency) requires optimistic locking on susceptible entities.
  - **Alternatives considered**: Pessimistic locking (rejected as overkill for product definitions).

- **Decision 4: Tabular Setup UI**
  - **Decision**: Use Thymeleaf dynamic form bindings (e.g., `*` syntax with indexed arrays like `nutritionLineItems[0].name`) to allow adding multiple rows in the product edit form, potentially aided by vanilla JS for adding/removing rows dynamically without full page reload.
  - **Rationale**: Fits the SSR Thymeleaf model while meeting the "without page reloads" requirement for adding rows in the browser.
  - **Alternatives considered**: React/Vue frontend (rejected as it violates the Thymeleaf SSR architecture).
