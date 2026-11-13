package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.dto.OfflineMutationDto;
import com.mushroom.stockkeeper.dto.OfflineSnapshotDto;
import com.mushroom.stockkeeper.dto.OfflineSyncRequest;
import com.mushroom.stockkeeper.dto.OfflineSyncResponse;
import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.NutritionLineItem;
import com.mushroom.stockkeeper.model.ProcessedOfflineMutation;
import com.mushroom.stockkeeper.model.ProcurementCycleNode;
import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProcessedOfflineMutationRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OfflineSyncService {

    private final ProductRepository productRepository;
    private final HarvestBatchRepository batchRepository;
    private final AppSettingRepository appSettingRepository;
    private final ProcessedOfflineMutationRepository processedMutationRepository;
    private final BatchService batchService;
    private final SettingsService settingsService;
    private final ProcurementSchedulerService procurementSchedulerService;
    private InventoryUnitRepository unitRepository;

    public OfflineSyncService(ProductRepository productRepository,
                              HarvestBatchRepository batchRepository,
                              AppSettingRepository appSettingRepository,
                              ProcessedOfflineMutationRepository processedMutationRepository,
                              BatchService batchService,
                              SettingsService settingsService,
                              ProcurementSchedulerService procurementSchedulerService) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.appSettingRepository = appSettingRepository;
        this.processedMutationRepository = processedMutationRepository;
        this.batchService = batchService;
        this.settingsService = settingsService;
        this.procurementSchedulerService = procurementSchedulerService;
    }

    @Autowired(required = false)
    public void setUnitRepository(InventoryUnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Transactional(readOnly = true)
    public OfflineSnapshotDto buildSnapshot() {
        OfflineSnapshotDto snapshot = new OfflineSnapshotDto();
        snapshot.setTimestamp(Instant.now().toString());

        // 1. Products (including nutrition line items)
        List<Product> products = productRepository.findAll();
        List<OfflineSnapshotDto.ProductSnapshotItem> productItems = new ArrayList<>(products.size());
        for (Product p : products) {
            List<OfflineSnapshotDto.NutritionLineSnapshotItem> nutritionItems = new ArrayList<>();
            if (p.getNutritionLineItems() != null) {
                for (NutritionLineItem item : p.getNutritionLineItems()) {
                    nutritionItems.add(new OfflineSnapshotDto.NutritionLineSnapshotItem(
                            item.getComponentName(),
                            item.getAmount(),
                            item.getMeasurementUnit()
                    ));
                }
            }
            productItems.add(new OfflineSnapshotDto.ProductSnapshotItem(
                    p.getId(),
                    p.getSku(),
                    p.getName(),
                    p.getDefaultExpiryDays(),
                    p.getFssaiLicenseNumber(),
                    p.getNetWeight(),
                    p.getNetWeightUom(),
                    p.getMrp(),
                    p.getStorageInstructions(),
                    p.isHasNutritionValues(),
                    p.getNutritionBaseUnitValue(),
                    p.getNutritionBaseUnitType(),
                    nutritionItems
            ));
        }
        snapshot.setProducts(productItems);

        // 2. Settings
        Map<String, String> settingsMap = new LinkedHashMap<>();
        for (AppSetting setting : appSettingRepository.findAll()) {
            settingsMap.put(setting.getSettingKey(), setting.getSettingValue());
        }
        settingsMap.putIfAbsent(SettingsService.KEY_LABEL_SHEET_SIZE, settingsService.getLabelSheetSize());
        settingsMap.putIfAbsent(SettingsService.KEY_CUSTOM_LABEL_WIDTH, String.valueOf(settingsService.getCustomLabelWidth()));
        settingsMap.putIfAbsent(SettingsService.KEY_CUSTOM_LABEL_HEIGHT, String.valueOf(settingsService.getCustomLabelHeight()));
        settingsMap.putIfAbsent(SettingsService.KEY_PROCUREMENT_BLACKOUT_DATES, settingsService.getProcurementBlackoutDatesRaw());
        snapshot.setSettings(settingsMap);
        snapshot.setManualLabelLastSequence(settingsService.getManualLabelLastSequence());

        // 3. Recent Batches (top 50)
        List<HarvestBatch> recent = batchRepository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "batchDate", "id"))).getContent();
        List<OfflineSnapshotDto.BatchSnapshotItem> batchItems = new ArrayList<>(recent.size());
        for (HarvestBatch b : recent) {
            batchItems.add(new OfflineSnapshotDto.BatchSnapshotItem(
                    b.getId(),
                    b.getBatchCode(),
                    b.getProduct() != null ? b.getProduct().getId() : null,
                    b.getProduct() != null ? b.getProduct().getName() : null,
                    b.getBatchDate(),
                    b.getExpiryDate(),
                    b.getTotalUnits()
            ));
        }
        snapshot.setRecentBatches(batchItems);

        // 4. Active Procurement Schedules
        List<ProcurementSchedule> schedules = procurementSchedulerService.getActiveSchedules();
        List<OfflineSnapshotDto.ProcurementScheduleSnapshotItem> scheduleItems = new ArrayList<>(schedules.size());
        for (ProcurementSchedule s : schedules) {
            List<OfflineSnapshotDto.ProcurementNodeSnapshotItem> nodeItems = new ArrayList<>();
            if (s.getNodes() != null) {
                for (ProcurementCycleNode n : s.getNodes()) {
                    nodeItems.add(new OfflineSnapshotDto.ProcurementNodeSnapshotItem(
                            n.getCycleIndex(),
                            n.getTheoreticalNeedDate(),
                            n.getActualDeliveryDate(),
                            n.getOrderCutoffDate(),
                            n.getUnadjustedActionDate(),
                            n.getActionDate(),
                            n.isManualOverride(),
                            n.getGapDays(),
                            n.isStockoutRisk(),
                            n.isHolidayAdjusted(),
                            n.getHolidayAdjustedDays()
                    ));
                }
            }
            scheduleItems.add(new OfflineSnapshotDto.ProcurementScheduleSnapshotItem(
                    s.getId(),
                    s.getScheduleName(),
                    s.getSupplyLastingDays(),
                    s.getVendorLeadTime(),
                    s.getActionBuffer(),
                    s.getPreferredDeliveryDay(),
                    nodeItems
            ));
        }
        snapshot.setProcurementSchedules(scheduleItems);

        return snapshot;
    }

    @Transactional
    public OfflineSyncResponse processSync(OfflineSyncRequest request) {
        int applied = 0;
        int skipped = 0;

        if (request != null && request.getMutations() != null) {
            for (OfflineMutationDto mutation : request.getMutations()) {
                if (mutation == null || mutation.getClientMutationId() == null || mutation.getClientMutationId().isBlank()) {
                    continue;
                }
                String mutationId = mutation.getClientMutationId().trim();
                if (processedMutationRepository.existsByClientMutationId(mutationId)) {
                    skipped++;
                    continue;
                }

                String summary = applySingleMutation(mutation);
                processedMutationRepository.save(new ProcessedOfflineMutation(
                        mutationId,
                        mutation.getMutationType() != null ? mutation.getMutationType() : "UNKNOWN",
                        summary
                ));
                applied++;
            }
        }

        String msg = String.format("Synced %d offline change(s) successfully (%d duplicate(s) skipped).", applied, skipped);
        return new OfflineSyncResponse(true, applied, skipped, msg, buildSnapshot());
    }

    private String applySingleMutation(OfflineMutationDto mutation) {
        String type = mutation.getMutationType() != null ? mutation.getMutationType().trim().toUpperCase() : "";
        Map<String, String> payload = mutation.getPayload() != null ? mutation.getPayload() : Map.of();

        return switch (type) {
            case "CREATE_BATCH" -> applyCreateBatch(payload);
            case "UPDATE_BATCH" -> applyUpdateBatch(payload);
            case "DELETE_BATCH" -> applyDeleteBatch(payload);
            case "SPOIL_UNIT" -> applySpoilUnit(payload);
            case "ALLOCATE_MANUAL_LABELS" -> applyAllocateManualLabels(payload);
            case "SET_MANUAL_LABEL_SEQUENCE" -> applySetManualLabelSequence(payload);
            case "CREATE_PROCUREMENT_SCHEDULE" -> applyCreateProcurementSchedule(payload);
            case "OVERRIDE_PROCUREMENT_DATE" -> applyOverrideProcurementDate(payload);
            case "UPDATE_BLACKOUT_DATES" -> applyUpdateBlackoutDates(payload);
            case "UPDATE_SETTINGS" -> applyUpdateSettings(payload);
            default -> "Ignored unknown mutation type: " + type;
        };
    }

    private String applyCreateBatch(Map<String, String> payload) {
        Long productId = Long.parseLong(payload.get("productId"));
        int quantity = Integer.parseInt(payload.get("quantity"));
        String batchDateStr = payload.get("batchDate");
        LocalDate batchDate = (batchDateStr != null && !batchDateStr.isBlank())
                ? LocalDate.parse(batchDateStr.trim())
                : LocalDate.now();
        String batchCode = payload.get("batchCode");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // If a batch with this exact offline batchCode already exists, skip creating a duplicate
        if (batchCode != null && !batchCode.isBlank() && batchRepository.findByBatchCode(batchCode.trim()).isPresent()) {
            return "Batch already exists: " + batchCode;
        }

        HarvestBatch created = batchService.createBatch(product, quantity, batchDate, batchCode);
        return "Created offline batch " + created.getBatchCode() + " (" + quantity + " units)";
    }

    private Optional<HarvestBatch> resolveTargetBatch(Map<String, String> payload) {
        String batchCode = payload.get("batchCode");
        if (batchCode != null && !batchCode.isBlank()) {
            Optional<HarvestBatch> byCode = batchRepository.findByBatchCode(batchCode.trim());
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        String batchIdStr = payload.get("batchId");
        if (batchIdStr != null && !batchIdStr.isBlank()) {
            try {
                long id = Long.parseLong(batchIdStr.trim());
                if (id > 0) {
                    return batchRepository.findById(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private String applyUpdateBatch(Map<String, String> payload) {
        Optional<HarvestBatch> batchOpt = resolveTargetBatch(payload);
        if (batchOpt.isEmpty()) {
            return "Skipped update: batch not found";
        }
        LocalDate newDate = LocalDate.parse(payload.get("batchDate").trim());
        try {
            batchService.updateBatch(batchOpt.get().getId(), newDate);
            return "Updated batch " + batchOpt.get().getBatchCode() + " date to " + newDate;
        } catch (Exception e) {
            return "Skipped batch update: " + e.getMessage();
        }
    }

    private String applyDeleteBatch(Map<String, String> payload) {
        Optional<HarvestBatch> batchOpt = resolveTargetBatch(payload);
        if (batchOpt.isEmpty()) {
            return "Skipped delete: batch not found";
        }
        HarvestBatch batch = batchOpt.get();
        try {
            batchService.deleteBatch(batch.getId());
            return "Deleted batch " + batch.getBatchCode();
        } catch (Exception e) {
            return "Skipped batch delete: " + e.getMessage();
        }
    }

    private String applySpoilUnit(Map<String, String> payload) {
        String unitUuid = payload.get("unitUuid");
        if (unitUuid != null && !unitUuid.isBlank() && unitRepository != null) {
            Optional<InventoryUnit> unitOpt = unitRepository.findByUuid(unitUuid.trim());
            if (unitOpt.isPresent()) {
                try {
                    batchService.markUnitSpoiled(unitOpt.get().getId());
                    return "Marked unit " + unitUuid + " as SPOILED";
                } catch (Exception e) {
                    return "Skipped spoil unit: " + e.getMessage();
                }
            }
        }
        String unitIdStr = payload.get("unitId");
        if (unitIdStr != null && !unitIdStr.isBlank()) {
            try {
                long unitId = Long.parseLong(unitIdStr.trim());
                if (unitId > 0) {
                    batchService.markUnitSpoiled(unitId);
                    return "Marked unit #" + unitId + " as SPOILED";
                }
            } catch (Exception ignored) {
            }
        }
        return "Skipped spoil unit: unit not found";
    }

    private String applyAllocateManualLabels(Map<String, String> payload) {
        int currentSeq = settingsService.getManualLabelLastSequence();
        String seqEndStr = payload.get("seqEnd");
        String qtyStr = payload.get("quantity");

        if (seqEndStr != null && !seqEndStr.isBlank()) {
            int offlineEndSeq = Integer.parseInt(seqEndStr.trim());
            int newSeq = Math.max(currentSeq, offlineEndSeq);
            settingsService.setManualLabelLastSequence(newSeq);
            return "Advanced manual label sequence to " + newSeq;
        } else if (qtyStr != null && !qtyStr.isBlank()) {
            int qty = Integer.parseInt(qtyStr.trim());
            settingsService.allocateManualLabelSequence(qty);
            return "Allocated " + qty + " manual labels";
        }
        return "No manual label sequence change";
    }

    private String applySetManualLabelSequence(Map<String, String> payload) {
        int lastSequence = Integer.parseInt(payload.getOrDefault("lastSequence", "0").trim());
        settingsService.setManualLabelLastSequence(Math.max(0, lastSequence));
        return "Updated manual label sequence to " + lastSequence;
    }

    private String applyCreateProcurementSchedule(Map<String, String> payload) {
        String scheduleName = payload.getOrDefault("scheduleName", "Offline Schedule");
        int supplyLastingDays = Integer.parseInt(payload.getOrDefault("supplyLastingDays", "28"));
        int vendorLeadTime = Integer.parseInt(payload.getOrDefault("vendorLeadTime", "15"));
        int actionBuffer = Integer.parseInt(payload.getOrDefault("actionBuffer", "3"));
        int preferredDeliveryDay = Integer.parseInt(payload.getOrDefault("preferredDeliveryDay", "6"));
        String initialDateStr = payload.get("initialDeliveryDate");
        LocalDate initialDeliveryDate = (initialDateStr != null && !initialDateStr.isBlank())
                ? LocalDate.parse(initialDateStr.trim())
                : null;

        ProcurementSchedule created = procurementSchedulerService.createSchedule(
                scheduleName,
                supplyLastingDays,
                vendorLeadTime,
                actionBuffer,
                preferredDeliveryDay,
                initialDeliveryDate
        );
        return "Created offline procurement schedule: " + created.getScheduleName();
    }

    private String applyOverrideProcurementDate(Map<String, String> payload) {
        int cycleIndex = Integer.parseInt(payload.getOrDefault("cycleIndex", "1"));
        LocalDate newDeliveryDate = LocalDate.parse(payload.get("newDeliveryDate").trim());

        Long targetScheduleId = null;
        String scheduleIdStr = payload.get("scheduleId");
        if (scheduleIdStr != null && !scheduleIdStr.isBlank()) {
            try {
                long parsedId = Long.parseLong(scheduleIdStr.trim());
                if (parsedId > 0 && procurementSchedulerService.getActiveScheduleById(parsedId).isPresent()) {
                    targetScheduleId = parsedId;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Fallback lookup by scheduleName if the schedule was created offline in an earlier mutation of the same batch
        if (targetScheduleId == null && payload.containsKey("scheduleName")) {
            String targetName = payload.get("scheduleName").trim();
            Optional<ProcurementSchedule> match = procurementSchedulerService.getActiveSchedules().stream()
                    .filter(s -> s.getScheduleName().equalsIgnoreCase(targetName))
                    .findFirst();
            if (match.isPresent()) {
                targetScheduleId = match.get().getId();
            }
        }

        if (targetScheduleId == null) {
            return "Skipped override: schedule not found";
        }

        procurementSchedulerService.overrideDeliveryDate(targetScheduleId, cycleIndex, newDeliveryDate);
        return "Overrode schedule " + targetScheduleId + " cycle " + cycleIndex + " to " + newDeliveryDate;
    }

    private String applyUpdateBlackoutDates(Map<String, String> payload) {
        String blackoutDates = payload.getOrDefault("blackoutDates", "");
        settingsService.setProcurementBlackoutDates(blackoutDates);
        procurementSchedulerService.recalculateAllActiveSchedules();
        return "Updated global holiday blackout dates";
    }

    private String applyUpdateSettings(Map<String, String> payload) {
        int count = 0;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("_") || "csrf".equalsIgnoreCase(key)) {
                continue;
            }
            settingsService.updateSetting(key, entry.getValue());
            count++;
        }
        if (payload.containsKey(SettingsService.KEY_PROCUREMENT_BLACKOUT_DATES)) {
            procurementSchedulerService.recalculateAllActiveSchedules();
        }
        return "Updated " + count + " application setting(s)";
    }
}
