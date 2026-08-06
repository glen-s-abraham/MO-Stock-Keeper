# Data Model & Interfaces: Default Monthly Snapshot

## Entities & DTOs

No new database tables, JPA entities, or DTOs are required for this enhancement.

This feature strictly modifies the presentation layer and default routing behavior of the existing `DashboardController`.

### Controller Interface Changes
The internal endpoint signature in `DashboardController` will change its default `tab` value:

**From:**
```java
@RequestParam(name = "tab", defaultValue = "lifetime") String tab
```

**To:**
```java
@RequestParam(name = "tab", defaultValue = "monthly") String tab
```

The underlying DTO (`ProductHarvestAggregateDto`) and repository queries (`getPeriodicHarvestTotals`) remain identical and fully leveraged.
