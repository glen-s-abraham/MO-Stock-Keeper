# Data Model & Interfaces: Snapshot Bargraphs

## 1. Data Transfer Objects (DTOs)

To pass data cleanly to the Chart.js frontend, we will introduce a response structure specifically for the charts.

### `ChartDatasetDto`
Represents a single product's data across the time periods.
```java
public class ChartDatasetDto {
    private String label; // Product Name
    private List<Long> data; // Harvest quantities matching the labels array
    private String backgroundColor; // Generated color for the bar
}
```

### `ChartResponseDto`
Represents the full payload expected by Chart.js.
```java
public class ChartResponseDto {
    private List<String> labels; // X-axis labels (e.g. "Week 1", "Week 2", or "Day 1"..."Day 31")
    private List<ChartDatasetDto> datasets; // The data lines/bars
}
```

### `TimeSeriesAggregateDto` (Internal Repository Projection)
Used by the Spring Data JPA repository to project the native query results before formatting them into the ChartResponseDto.
```java
public class TimeSeriesAggregateDto {
    private String productName;
    private Integer timeBucket; // e.g., 1 (for 1st day/month), or 32 (for week 32)
    private Long totalQuantity;
}
```

## 2. API Endpoints (Internal Contracts)

The `DashboardController` will expose an internal JSON endpoint for the frontend Javascript to call.

### `GET /dashboard/harvest/chart-data`

**Parameters:**
- `tab`: (monthly | yearly)
- `distribution`: (daily | weekly | monthly)
- `year`: (Integer)
- `month`: (Integer, optional)

**Response Payload (`ChartResponseDto`):**
```json
{
  "labels": ["Day 1", "Day 2", "Day 3"],
  "datasets": [
    {
      "label": "Button Mushroom",
      "data": [150, 0, 200],
      "backgroundColor": "#4e73df"
    },
    {
      "label": "Oyster Mushroom",
      "data": [50, 100, 75],
      "backgroundColor": "#1cc88a"
    }
  ]
}
```
