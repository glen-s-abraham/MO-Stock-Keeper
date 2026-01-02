package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class InventoryUnitRepositoryTest {

    @Autowired
    private InventoryUnitRepository unitRepository;

    @Autowired
    private HarvestBatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UOMRepository uomRepository;

    @Test
    void findByUuidForUpdate_ShouldReturnUnit() {
        // Setup
        UOM uom = new UOM();
        uom.setCode("kg");
        uomRepository.save(uom);

        Product p = new Product();
        p.setName("Mushrooms");
        p.setSku("MUSH-001");
        p.setUom(uom);
        p.setRetailPrice(BigDecimal.TEN);
        productRepository.save(p);

        HarvestBatch batch = new HarvestBatch();
        batch.setBatchCode("B-001");
        batch.setProduct(p);
        batch.setBatchDate(LocalDate.now());
        batchRepository.save(batch);

        InventoryUnit unit = new InventoryUnit();
        unit.setUuid("U:TEST-LOCK");
        unit.setBatch(batch);
        unit.setStatus(InventoryStatus.AVAILABLE);
        unit.setQrCodeContent("QR-CODE-1");
        unitRepository.save(unit);

        // Test
        Optional<InventoryUnit> result = unitRepository.findByUuidForUpdate("U:TEST-LOCK");

        assertTrue(result.isPresent());
        assertEquals("U:TEST-LOCK", result.get().getUuid());
    }

    @Test
    void findByBatchId_ShouldReturnUnits() {
        // Setup (re-use similar setup or separate)
        UOM uom = new UOM();
        uom.setCode("kg2");
        uomRepository.save(uom);

        Product p = new Product();
        p.setName("Mushrooms2");
        p.setSku("MUSH-002");
        p.setUom(uom);
        productRepository.save(p);

        HarvestBatch batch = new HarvestBatch();
        batch.setBatchCode("B-002");
        batch.setProduct(p);
        batch.setBatchDate(LocalDate.now());
        batch = batchRepository.save(batch); // Save and get ID

        InventoryUnit u1 = new InventoryUnit();
        u1.setBatch(batch);
        u1.setUuid("U:1");
        u1.setStatus(InventoryStatus.AVAILABLE);
        u1.setQrCodeContent("QR-1");
        unitRepository.save(u1);

        InventoryUnit u2 = new InventoryUnit();
        u2.setBatch(batch);
        u2.setUuid("U:2");
        u2.setStatus(InventoryStatus.AVAILABLE);
        u2.setQrCodeContent("QR-2");
        unitRepository.save(u2);

        // Test
        List<InventoryUnit> units = unitRepository.findByBatchId(batch.getId());

        assertEquals(2, units.size());
    }
}
