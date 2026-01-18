package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BatchServiceTest {

    @Mock
    private HarvestBatchRepository batchRepository;

    @Mock
    private InventoryUnitRepository unitRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BatchService batchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createBatch_allowsToday() {
        Product product = new Product();
        product.setId(1L);
        when(batchRepository.save(any(HarvestBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> batchService.createBatch(product, 10, LocalDate.now()));
    }

    @Test
    void createBatch_allowsTomorrow() {
        Product product = new Product();
        product.setId(1L);
        when(batchRepository.save(any(HarvestBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> batchService.createBatch(product, 10, LocalDate.now().plusDays(1)));
    }

    @Test
    void createBatch_disallowsDayAfterTomorrow() {
        Product product = new Product();
        product.setId(1L);

        assertThrows(IllegalArgumentException.class,
                () -> batchService.createBatch(product, 10, LocalDate.now().plusDays(2)));
    }

    @Test
    void updateBatch_allowsTomorrow() {
        HarvestBatch batch = new HarvestBatch();
        batch.setId(1L);
        batch.setBatchDate(LocalDate.now());

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertDoesNotThrow(() -> batchService.updateBatch(1L, LocalDate.now().plusDays(1)));
    }

    @Test
    void updateBatch_disallowsDayAfterTomorrow() {
        HarvestBatch batch = new HarvestBatch();
        batch.setId(1L);
        batch.setBatchDate(LocalDate.now());

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThrows(IllegalArgumentException.class, () -> batchService.updateBatch(1L, LocalDate.now().plusDays(2)));
    }
}
