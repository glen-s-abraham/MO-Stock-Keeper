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
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class BatchService {

    private final HarvestBatchRepository batchRepository;
    private final InventoryUnitRepository unitRepository;

    public BatchService(HarvestBatchRepository batchRepository, InventoryUnitRepository unitRepository) {
        this.batchRepository = batchRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional
    public HarvestBatch createBatch(Product product, int quantity, LocalDate batchDate) {
        // Create Batch
        HarvestBatch batch = new HarvestBatch();
        batch.setProduct(product);
        batch.setTotalUnits(quantity);
        batch.setBatchDate(batchDate != null ? batchDate : LocalDate.now());

        // Calculate Expiry
        if (product.getDefaultExpiryDays() != null) {
            batch.setExpiryDays(product.getDefaultExpiryDays());
            batch.setExpiryDate(batch.getBatchDate().plusDays(product.getDefaultExpiryDays()));
        }

        // Generate Batch Code: B-YYYYMMDD-SEQ
        String dateStr = batch.getBatchDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        long count = batchRepository.count(); // Simple sequence for now
        String batchCode = "B-" + dateStr + "-" + (count + 1);
        batch.setBatchCode(batchCode);

        HarvestBatch savedBatch = batchRepository.save(batch);

        // Generate Inventory Units
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

            unitRepository.save(unit);
        });

        return savedBatch;
    }

    @Transactional
    public void deleteBatch(Long batchId) throws Exception {
        HarvestBatch batch = batchRepository.findById(batchId).orElseThrow();

        // Check for usage
        long usedUnits = unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(batchId))
                .filter(u -> u.getStatus() != InventoryStatus.AVAILABLE)
                .count();

        if (usedUnits > 0) {
            throw new Exception("Cannot delete batch. " + usedUnits + " units are Sold or allocated.");
        }

        // Delete all units
        unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(batchId))
                .forEach(unitRepository::delete);

        // Delete batch
        batchRepository.delete(batch);
    }

    @Transactional
    public void updateBatch(Long batchId, LocalDate newDate) {
        HarvestBatch batch = batchRepository.findById(batchId).orElseThrow();
        batch.setBatchDate(newDate);

        // Recalculate expiry
        if (batch.getExpiryDays() != null) {
            batch.setExpiryDate(newDate.plusDays(batch.getExpiryDays()));
        }

        batchRepository.save(batch);
    }
}
