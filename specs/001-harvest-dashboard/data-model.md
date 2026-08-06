# Data Model & Interfaces: Harvest Dashboard

## Entities & DTOs

The Harvest Dashboard feature is primarily a read-only view that aggregates existing data. No new database tables or JPA entities are required. Instead, it relies on Data Transfer Objects (DTOs) to capture the aggregated results directly from the database engine.

### 1. `ProductHarvestAggregateDto`
Used to transfer the aggregated results of a query for any given timeframe (lifetime, yearly, or monthly).

**Fields:**
- `productName` (String): The name of the product.
- `totalQuantity` (BigDecimal/Long depending on inventory precision): The sum of the harvested amounts for this product over the requested period.

### 2. Dashboard View Model (Thymeleaf Context)
The controller will assemble a view model containing the necessary data to render the dashboard.

**Attributes:**
- `lifetimeTotals` (List<ProductHarvestAggregateDto>): Data for the primary lifetime dashboard section.
- `snapshotTotals` (List<ProductHarvestAggregateDto>): Data for the selected snapshot tab (Monthly or Yearly).
- `activeTab` (String): Indicator of which tab is currently selected (`monthly` or `yearly`).
- `selectedMonth` (Integer): The selected month (1-12) if the monthly tab is active.
- `selectedYear` (Integer): The selected year if either tab is active.
- `availableYears` (List<Integer>): List of years available for the dropdown selectors, populated by checking the earliest and latest recorded harvests.

## Aggregation Queries (Conceptual)

The system will implement Spring Data repository methods using `@Query` to perform aggregations.

### Lifetime Harvest
```sql
SELECT new ...ProductHarvestAggregateDto(p.name, SUM(u.quantity))
FROM InventoryUnit u JOIN u.product p
WHERE u.state = 'HARVESTED' -- (Assuming a state filter is needed based on domain)
GROUP BY p.name
```

### Yearly/Monthly Snapshot
For targeted periods, the queries will filter by date ranges corresponding to the selected month/year or just the year to avoid complex DB-specific date extraction functions where possible:
```sql
SELECT new ...ProductHarvestAggregateDto(p.name, SUM(u.quantity))
FROM InventoryUnit u JOIN u.product p
WHERE u.harvestDate >= :startDate AND u.harvestDate < :endDate
GROUP BY p.name
```

## State Transitions
N/A - The dashboard is strictly read-only and does not mutate any system state.
