package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.dto.OfflineMutationDto;
import com.mushroom.stockkeeper.dto.OfflineSnapshotDto;
import com.mushroom.stockkeeper.dto.OfflineSyncRequest;
import com.mushroom.stockkeeper.dto.OfflineSyncResponse;
import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.ProcessedOfflineMutation;
import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProcessedOfflineMutationRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineSyncServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private HarvestBatchRepository batchRepository;

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private ProcessedOfflineMutationRepository processedMutationRepository;

    @Mock
    private BatchService batchService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private ProcurementSchedulerService procurementSchedulerService;

    @Mock
    private InventoryUnitRepository unitRepository;

    private OfflineSyncService offlineSyncService;

    private Product sampleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setSku("MUSH-01");
        p.setName("Organic Oyster Mushroom");
        p.setDefaultExpiryDays(5);
        p.setFssaiLicenseNumber("12345678901234");
        p.setNetWeight(new BigDecimal("200"));
        p.setNetWeightUom("g");
        p.setMrp(new BigDecimal("120.00"));
        return p;
    }

    @BeforeEach
    void setUp() {
        offlineSyncService = new OfflineSyncService(
                productRepository,
                batchRepository,
                appSettingRepository,
                processedMutationRepository,
                batchService,
                settingsService,
                procurementSchedulerService
        );
        offlineSyncService.setUnitRepository(unitRepository);

        lenient().when(productRepository.findAll()).thenReturn(List.of(sampleProduct()));
        lenient().when(appSettingRepository.findAll()).thenReturn(List.of(
                new AppSetting("company_name", "MO Farms")
        ));
        lenient().when(settingsService.getLabelSheetSize()).thenReturn("THERMAL_CUSTOM");
        lenient().when(settingsService.getCustomLabelWidth()).thenReturn(75);
        lenient().when(settingsService.getCustomLabelHeight()).thenReturn(75);
        lenient().when(settingsService.getProcurementBlackoutDatesRaw()).thenReturn("2026-12-25");
        lenient().when(settingsService.getManualLabelLastSequence()).thenReturn(10);
        lenient().when(batchRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        lenient().when(procurementSchedulerService.getActiveSchedules()).thenReturn(List.of());
    }

    @Test
    void buildSnapshot_ShouldIncludeProductsSettingsManualSequenceAndSchedules() {
        OfflineSnapshotDto snapshot = offlineSyncService.buildSnapshot();

        assertNotNull(snapshot.getTimestamp());
        assertEquals(1, snapshot.getProducts().size());
        assertEquals("Organic Oyster Mushroom", snapshot.getProducts().get(0).getName());
        assertEquals("MO Farms", snapshot.getSettings().get("company_name"));
        assertEquals(10, snapshot.getManualLabelLastSequence());
    }

    @Test
    void processSync_CreateBatch_ShouldPreserveOfflineBatchCodeAndRecordProcessedMutation() {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCode("B-20261113-999111")).thenReturn(Optional.empty());
        when(processedMutationRepository.existsByClientMutationId("mut-batch-1")).thenReturn(false);

        HarvestBatch created = new HarvestBatch();
        created.setId(5L);
        created.setBatchCode("B-20261113-999111");
        created.setProduct(product);
        created.setTotalUnits(25);
        when(batchService.createBatch(eq(product), eq(25), eq(LocalDate.of(2026, 11, 13)), eq("B-20261113-999111")))
                .thenReturn(created);

        OfflineMutationDto mutation = new OfflineMutationDto(
                "mut-batch-1",
                "CREATE_BATCH",
                "2026-11-13T10:00:00Z",
                Map.of(
                        "productId", "1",
                        "quantity", "25",
                        "batchDate", "2026-11-13",
                        "batchCode", "B-20261113-999111"
                )
        );

        OfflineSyncResponse response = offlineSyncService.processSync(new OfflineSyncRequest(List.of(mutation)));

        assertTrue(response.isSuccess());
        assertEquals(1, response.getAppliedCount());
        assertEquals(0, response.getSkippedDuplicateCount());
        verify(batchService).createBatch(product, 25, LocalDate.of(2026, 11, 13), "B-20261113-999111");
        verify(processedMutationRepository).save(any(ProcessedOfflineMutation.class));
    }

    @Test
    void processSync_WhenDuplicateClientMutationId_ShouldSkipIdempotently() {
        when(processedMutationRepository.existsByClientMutationId("mut-dup-1")).thenReturn(true);

        OfflineMutationDto duplicateMutation = new OfflineMutationDto(
                "mut-dup-1",
                "CREATE_BATCH",
                "2026-11-13T10:00:00Z",
                Map.of("productId", "1", "quantity", "10", "batchCode", "B-20261113-111")
        );

        OfflineSyncResponse response = offlineSyncService.processSync(new OfflineSyncRequest(List.of(duplicateMutation)));

        assertTrue(response.isSuccess());
        assertEquals(0, response.getAppliedCount());
        assertEquals(1, response.getSkippedDuplicateCount());
        verify(batchService, never()).createBatch(any(), anyInt(), any(), any());
    }

    @Test
    void processSync_AllocateManualLabelsAndProcurementAndSettings_ShouldApplyAllInOrder() {
        when(processedMutationRepository.existsByClientMutationId(anyString())).thenReturn(false);
        when(settingsService.getManualLabelLastSequence()).thenReturn(10);

        ProcurementSchedule sched = new ProcurementSchedule();
        sched.setId(2L);
        sched.setScheduleName("Raw Substrate - 175kg");
        when(procurementSchedulerService.getActiveScheduleById(2L)).thenReturn(Optional.of(sched));

        OfflineMutationDto m1 = new OfflineMutationDto(
                "mut-manual-1",
                "ALLOCATE_MANUAL_LABELS",
                "2026-11-13T10:01:00Z",
                Map.of("productId", "1", "quantity", "15", "seqStart", "11", "seqEnd", "25")
        );
        OfflineMutationDto m2 = new OfflineMutationDto(
                "mut-arps-override-1",
                "OVERRIDE_PROCUREMENT_DATE",
                "2026-11-13T10:02:00Z",
                Map.of("scheduleId", "2", "cycleIndex", "1", "newDeliveryDate", "2026-11-21")
        );
        OfflineMutationDto m3 = new OfflineMutationDto(
                "mut-settings-1",
                "UPDATE_SETTINGS",
                "2026-11-13T10:03:00Z",
                Map.of("company_name", "MO Farms Updated", "procurement_blackout_dates", "2026-12-25")
        );

        OfflineSyncResponse response = offlineSyncService.processSync(new OfflineSyncRequest(List.of(m1, m2, m3)));

        assertTrue(response.isSuccess());
        assertEquals(3, response.getAppliedCount());
        verify(settingsService).setManualLabelLastSequence(25);
        verify(procurementSchedulerService).overrideDeliveryDate(2L, 1, LocalDate.of(2026, 11, 21));
        verify(settingsService).updateSetting("company_name", "MO Farms Updated");
        verify(procurementSchedulerService).recalculateAllActiveSchedules();
    }

    @Test
    void processSync_UpdateDeleteBatchAndSpoilUnit_ShouldResolveByBatchCodeAndApply() throws Exception {
        when(processedMutationRepository.existsByClientMutationId(anyString())).thenReturn(false);

        HarvestBatch batch = new HarvestBatch();
        batch.setId(42L);
        batch.setBatchCode("B-20261113-999111");
        InventoryUnit unit = new InventoryUnit();
        unit.setId(101L);
        unit.setUuid("B-20261113-999111-1");

        when(batchRepository.findByBatchCode("B-20261113-999111")).thenReturn(Optional.of(batch));
        when(unitRepository.findByUuid("B-20261113-999111-1")).thenReturn(Optional.of(unit));

        OfflineMutationDto updateMut = new OfflineMutationDto(
                "mut-upd-batch-1",
                "UPDATE_BATCH",
                "2026-11-13T10:05:00Z",
                Map.of("batchCode", "B-20261113-999111", "batchDate", "2026-11-14")
        );
        OfflineMutationDto spoilMut = new OfflineMutationDto(
                "mut-spoil-unit-1",
                "SPOIL_UNIT",
                "2026-11-13T10:06:00Z",
                Map.of("batchCode", "B-20261113-999111", "unitUuid", "B-20261113-999111-1")
        );
        OfflineMutationDto deleteMut = new OfflineMutationDto(
                "mut-del-batch-1",
                "DELETE_BATCH",
                "2026-11-13T10:07:00Z",
                Map.of("batchCode", "B-20261113-999111")
        );

        OfflineSyncResponse response = offlineSyncService.processSync(
                new OfflineSyncRequest(List.of(updateMut, spoilMut, deleteMut))
        );

        assertTrue(response.isSuccess());
        assertEquals(3, response.getAppliedCount());
        verify(batchService).updateBatch(42L, LocalDate.of(2026, 11, 14));
        verify(batchService).markUnitSpoiled(101L);
        verify(batchService).deleteBatch(42L);
    }
}
