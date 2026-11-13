package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSnapshotDto {

    private String timestamp;
    private List<ProductSnapshotItem> products = new ArrayList<>();
    private Map<String, String> settings = new LinkedHashMap<>();
    private int manualLabelLastSequence;
    private List<BatchSnapshotItem> recentBatches = new ArrayList<>();
    private List<ProcurementScheduleSnapshotItem> procurementSchedules = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionLineSnapshotItem {
        private String componentName;
        private BigDecimal amount;
        private String measurementUnit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSnapshotItem {
        private Long id;
        private String sku;
        private String name;
        private Integer defaultExpiryDays;
        private String fssaiLicenseNumber;
        private BigDecimal netWeight;
        private String netWeightUom;
        private BigDecimal mrp;
        private String storageInstructions;
        private boolean hasNutritionValues;
        private BigDecimal nutritionBaseUnitValue;
        private String nutritionBaseUnitType;
        private List<NutritionLineSnapshotItem> nutritionLineItems = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchSnapshotItem {
        private Long id;
        private String batchCode;
        private Long productId;
        private String productName;
        private LocalDate batchDate;
        private LocalDate expiryDate;
        private Integer totalUnits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcurementNodeSnapshotItem {
        private Integer cycleIndex;
        private LocalDate theoreticalNeedDate;
        private LocalDate actualDeliveryDate;
        private LocalDate orderCutoffDate;
        private LocalDate unadjustedActionDate;
        private LocalDate actionDate;
        private boolean manualOverride;
        private Integer gapDays;
        private boolean stockoutRisk;
        private boolean holidayAdjusted;
        private Integer holidayAdjustedDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcurementScheduleSnapshotItem {
        private Long id;
        private String scheduleName;
        private Integer supplyLastingDays;
        private Integer vendorLeadTime;
        private Integer actionBuffer;
        private Integer preferredDeliveryDay;
        private List<ProcurementNodeSnapshotItem> nodes = new ArrayList<>();
    }
}
