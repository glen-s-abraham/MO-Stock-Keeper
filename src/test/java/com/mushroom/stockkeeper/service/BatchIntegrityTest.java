package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryStatus;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@Transactional
public class BatchIntegrityTest {

    @Autowired
    private BatchService batchService;

    @Autowired
    private HarvestBatchRepository batchRepository;

    @Autowired
    private InventoryUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.mushroom.stockkeeper.repository.UOMRepository uomRepository;

    @MockBean
    private AuditService auditService; // Mock audit to keep logs clean

    private Product createProduct(String name, String sku) {
        // Create UOM if not exists
        com.mushroom.stockkeeper.model.UOM uom = uomRepository.findByCode("KG").orElseGet(() -> {
            com.mushroom.stockkeeper.model.UOM newUom = new com.mushroom.stockkeeper.model.UOM();
            newUom.setCode("KG");
            return uomRepository.save(newUom);
        });

        Product p = new Product();
        p.setName(name);
        p.setSku(sku);
        p.setUom(uom);
        p.setDefaultExpiryDays(5);
        return productRepository.save(p);
    }

    @Test
    public void testCreateBatchIntegrity() {
        // 1. Setup Data
        Product p = createProduct("Mushrooms A", "MUSH-A");

        // 2. Create Batch (10 Units)
        HarvestBatch batch = batchService.createBatch(p, 10, LocalDate.now());

        // 3. Verify Batch
        Assertions.assertNotNull(batch.getId());
        Assertions.assertEquals(10, batch.getTotalUnits());
        Assertions.assertEquals(LocalDate.now().plusDays(5), batch.getExpiryDate());

        // 4. Verify Units
        List<InventoryUnit> units = unitRepository.findAll();
        long batchUnits = units.stream().filter(u -> u.getBatch().getId().equals(batch.getId())).count();
        Assertions.assertEquals(10, batchUnits);

        InventoryUnit first = units.stream().filter(u -> u.getBatch().getId().equals(batch.getId())).findFirst().get();
        Assertions.assertEquals(InventoryStatus.AVAILABLE, first.getStatus());
        Assertions.assertTrue(first.getUuid().startsWith(batch.getBatchCode()));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    public void testBlockDeleteIfSold() throws Exception {
        // 1. Setup
        Product p = createProduct("Mushrooms B", "MUSH-B");
        HarvestBatch batch = batchService.createBatch(p, 5, LocalDate.now());

        // 2. Sell one unit
        InventoryUnit unit = unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(batch.getId()))
                .findFirst().get();

        unit.setStatus(InventoryStatus.SOLD);
        unitRepository.save(unit);

        // 3. Attempt Delete - Should Fail
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            batchService.deleteBatch(batch.getId());
        });

        System.out.println("Blocked Delete Message: " + exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("Cannot delete batch"));
    }

    @Test
    public void testBlockUpdateIfSold() throws Exception {
        // 1. Setup
        Product p = createProduct("Mushrooms C", "MUSH-C");
        HarvestBatch batch = batchService.createBatch(p, 5, LocalDate.now());

        // 2. Sell one unit
        InventoryUnit unit = unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(batch.getId()))
                .findFirst().get();

        unit.setStatus(InventoryStatus.SOLD);
        unitRepository.save(unit);

        // 3. Attempt Update Date - Should Fail
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            batchService.updateBatch(batch.getId(), LocalDate.now().minusDays(1));
        });

        System.out.println("Blocked Update Message: " + exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("Cannot update batch date"));
    }
}
