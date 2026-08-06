package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private InventoryUnitRepository inventoryUnitRepository;

    @Transactional(readOnly = true)
    public List<ProductHarvestAggregateDto> getLifetimeHarvestTotals() {
        return inventoryUnitRepository.getLifetimeHarvestTotals();
    }

    @Transactional(readOnly = true)
    public List<ProductHarvestAggregateDto> getMonthlyHarvestTotals(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);
        return inventoryUnitRepository.getPeriodicHarvestTotals(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<ProductHarvestAggregateDto> getYearlyHarvestTotals(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = startDate.plusYears(1);
        return inventoryUnitRepository.getPeriodicHarvestTotals(startDate, endDate);
    }
    
    @Transactional(readOnly = true)
    public List<Integer> getAvailableHarvestYears() {
        // Mocking for now as the repository doesn't have min/max batch date yet
        return List.of(2023, 2024, 2025, 2026);
    }

    @Transactional(readOnly = true)
    public com.mushroom.stockkeeper.dto.ChartResponseDto getChartData(String tab, String distribution, int year, Integer month) {
        List<com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto> rawData;
        List<String> labels = new java.util.ArrayList<>();
        int dataSize = 0;
        java.util.function.Function<Integer, Integer> indexMapper;

        if ("monthly".equals(tab) && "daily".equals(distribution)) {
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.plusMonths(1);
            rawData = inventoryUnitRepository.getDailyHarvestTotals(startDate, endDate);
            dataSize = startDate.lengthOfMonth();
            for (int i = 1; i <= dataSize; i++) labels.add(String.valueOf(i));
            indexMapper = bucket -> (bucket != null ? bucket - 1 : -1);
        } else if ("monthly".equals(tab) && "weekly".equals(distribution)) {
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.plusMonths(1);
            rawData = inventoryUnitRepository.getWeeklyHarvestTotals(startDate, endDate);
            
            // Map distinct week-of-year to sequential indices for the month
            java.util.List<Integer> distinctWeeks = rawData.stream()
                .map(com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto::getTimeBucket)
                .filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();
                
            dataSize = distinctWeeks.size();
            for (int i = 1; i <= dataSize; i++) labels.add("Week " + i);
            indexMapper = bucket -> distinctWeeks.indexOf(bucket);
        } else if ("yearly".equals(tab) && "monthly".equals(distribution)) {
            LocalDate startDate = LocalDate.of(year, 1, 1);
            LocalDate endDate = startDate.plusYears(1);
            rawData = inventoryUnitRepository.getMonthlyDistributionTotals(startDate, endDate);
            dataSize = 12;
            labels = java.util.List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
            indexMapper = bucket -> (bucket != null ? bucket - 1 : -1);
        } else {
            return new com.mushroom.stockkeeper.dto.ChartResponseDto(java.util.Collections.emptyList(), java.util.Collections.emptyList());
        }

        return formatChartResponse(rawData, labels, dataSize, indexMapper);
    }

    private com.mushroom.stockkeeper.dto.ChartResponseDto formatChartResponse(
            List<com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto> rawData,
            List<String> labels,
            int dataSize,
            java.util.function.Function<Integer, Integer> indexMapper) {
        
        String[] colors = {"#4e73df", "#1cc88a", "#36b9cc", "#f6c23e", "#e74a3b"};
        java.util.Map<String, com.mushroom.stockkeeper.dto.ChartDatasetDto> datasets = new java.util.HashMap<>();
        
        int colorIdx = 0;
        for (com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto dto : rawData) {
            String product = dto.getProductName();
            if (!datasets.containsKey(product)) {
                com.mushroom.stockkeeper.dto.ChartDatasetDto ds = new com.mushroom.stockkeeper.dto.ChartDatasetDto();
                ds.setLabel(product);
                ds.setBackgroundColor(colors[colorIdx % colors.length]);
                List<Long> data = new java.util.ArrayList<>(java.util.Collections.nCopies(dataSize, 0L));
                ds.setData(data);
                datasets.put(product, ds);
                colorIdx++;
            }
            
            Integer idx = indexMapper.apply(dto.getTimeBucket());
            if (idx != null && idx >= 0 && idx < dataSize) {
                datasets.get(product).getData().set(idx, dto.getTotalQuantity());
            }
        }
        
        return new com.mushroom.stockkeeper.dto.ChartResponseDto(labels, new java.util.ArrayList<>(datasets.values()));
    }
}
