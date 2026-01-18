package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryStatus;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;

@Service
public class BatchService {

    private final HarvestBatchRepository batchRepository;
    private final InventoryUnitRepository unitRepository;
    private final AuditService auditService;

    public BatchService(HarvestBatchRepository batchRepository, InventoryUnitRepository unitRepository,
            AuditService auditService) {
        this.batchRepository = batchRepository;
        this.unitRepository = unitRepository;
        this.auditService = auditService;
    }

    @Transactional
    public HarvestBatch createBatch(Product product, int quantity, LocalDate batchDate) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (quantity > 5000) {
            throw new IllegalArgumentException("Batch size limit exceeded. Max 5000 units per batch.");
        }
        // Create Batch
        HarvestBatch batch = new HarvestBatch();
        batch.setProduct(product);
        batch.setTotalUnits(quantity);
        batch.setBatchDate(batchDate != null ? batchDate : LocalDate.now());

        // Edge Case: Future Dates - Allow packing for tomorrow (1 day buffer)
        if (batch.getBatchDate().isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Harvest date cannot be more than 1 day in the future.");
        }

        // Calculate Expiry
        if (product.getDefaultExpiryDays() != null) {
            batch.setExpiryDays(product.getDefaultExpiryDays());
            batch.setExpiryDate(batch.getBatchDate().plusDays(product.getDefaultExpiryDays()));
        }

        // Generate Batch Code: B-YYYYMMDD-TIME
        // e.g., B-20231222-1703239999
        // This avoids race conditions with count() and unique constraint violations
        String dateStr = batch.getBatchDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(6); // shorter suffix
        String batchCode = "B-" + dateStr + "-" + uniqueSuffix;
        batch.setBatchCode(batchCode);

        HarvestBatch savedBatch = batchRepository.save(batch);

        // Generate Inventory Units
        java.util.List<InventoryUnit> unitsToSave = new java.util.ArrayList<>(quantity);
        IntStream.range(0, quantity).forEach(i -> {
            InventoryUnit unit = new InventoryUnit();
            unit.setBatch(savedBatch);
            unit.setStatus(InventoryStatus.AVAILABLE);

            // Sequential UUID: BatchCode-Sequence
            // e.g. B-20231219-1-1, B-20231219-1-2
            String uuid = savedBatch.getBatchCode() + "-" + (i + 1);
            unit.setUuid(uuid);

            // QR Content: JSON-like or simple pipe sep?
            // "ID:UUID|BATCH:CODE|PROD:SKU"
            String qrContent = "U:" + uuid; // Keep it short for better scanning
            unit.setQrCodeContent(qrContent);

            unitsToSave.add(unit);
        });

        unitRepository.saveAll(unitsToSave);

        return savedBatch;
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void deleteBatch(Long batchId) throws Exception {
        HarvestBatch batch = batchRepository.findById(batchId).orElseThrow();

        // Check for usage (Optimized Query)
        long usedUnits = unitRepository.countByBatchIdAndStatusNot(batchId, InventoryStatus.AVAILABLE);

        if (usedUnits > 0) {
            throw new Exception("Cannot delete batch. " + usedUnits + " units are Sold or allocated.");
        }

        // Delete all units (Optimized Query)
        unitRepository.deleteByBatchId(batchId);

        // Audit
        auditService.log("DELETE_BATCH", "Deleted Batch " + batchId + " (" + batch.getBatchCode() + ")");

        // Delete batch
        batchRepository.delete(batch);
    }

    @Transactional
    public void updateBatch(Long batchId, LocalDate newDate) throws Exception {
        HarvestBatch batch = batchRepository.findById(batchId).orElseThrow();

        // 1. Validate Future Date (Allow tomorrow)
        if (newDate.isAfter(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Harvest date cannot be more than 1 day in the future.");
        }

        // 2. Check Integrity: Cannot update if items are sold/returned
        long usedUnits = unitRepository.countByBatchIdAndStatusNot(batchId, InventoryStatus.AVAILABLE);
        if (usedUnits > 0) {
            throw new Exception(
                    "Cannot update batch date. " + usedUnits + " units have already been processed (Sold/Spoiled).");
        }

        LocalDate oldDate = batch.getBatchDate();
        batch.setBatchDate(newDate);

        // Recalculate expiry
        if (batch.getExpiryDays() != null) {
            batch.setExpiryDate(newDate.plusDays(batch.getExpiryDays()));
        }

        batchRepository.save(batch);

        auditService.log("UPDATE_BATCH",
                "Updated Batch " + batch.getBatchCode() + " Date from " + oldDate + " to " + newDate);
    }

    @Transactional
    public void markUnitSpoiled(Long unitId) {
        InventoryUnit unit = unitRepository.findById(unitId).orElseThrow();
        if (unit.getStatus() != InventoryStatus.AVAILABLE) {
            throw new IllegalStateException("Only available units can be marked as spoiled.");
        }
        unit.setStatus(InventoryStatus.SPOILED);
        unitRepository.save(unit);
        auditService.log("UNIT_SPOILED", "Marked Unit " + unit.getUuid() + " as SPOILED");
    }
}
