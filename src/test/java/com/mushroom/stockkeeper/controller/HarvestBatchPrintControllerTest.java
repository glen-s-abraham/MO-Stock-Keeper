package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.PrintServerStatusResponse;
import com.mushroom.stockkeeper.dto.ServerPrintResponse;
import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import com.mushroom.stockkeeper.service.BatchService;
import com.mushroom.stockkeeper.service.PrintServerService;
import com.mushroom.stockkeeper.service.QrCodeService;
import com.mushroom.stockkeeper.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HarvestBatchPrintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HarvestBatchRepository batchRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private InventoryUnitRepository unitRepository;

    @MockBean
    private BatchService batchService;

    @MockBean
    private QrCodeService qrCodeService;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private PrintServerService printServerService;

    @MockBean
    private AppSettingRepository appSettingRepository;

    private HarvestBatch sampleBatch() {
        Product p = new Product();
        p.setId(1L);
        p.setName("Button Mushroom");
        p.setHasNutritionValues(false);

        HarvestBatch batch = new HarvestBatch();
        batch.setId(5L);
        batch.setBatchCode("B-20261020-005");
        batch.setBatchDate(LocalDate.of(2026, 10, 20));
        batch.setExpiryDate(LocalDate.of(2026, 10, 27));
        batch.setProduct(p);
        return batch;
    }

    private List<InventoryUnit> sampleUnits(HarvestBatch batch) {
        InventoryUnit u1 = new InventoryUnit();
        u1.setId(101L);
        u1.setUuid("B-20261020-005-001");
        u1.setQrCodeContent("U:B-20261020-005-001");
        u1.setBatch(batch);

        InventoryUnit u2 = new InventoryUnit();
        u2.setId(102L);
        u2.setUuid("B-20261020-005-002");
        u2.setQrCodeContent("U:B-20261020-005-002");
        u2.setBatch(batch);

        return List.of(u1, u2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void printLabelsView_WhenRemoteClientAndPrintServerAvailable_ShouldShowServerPrintAndHideClientPrint() throws Exception {
        HarvestBatch batch = sampleBatch();
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(unitRepository.findByBatchId(5L)).thenReturn(sampleUnits(batch));
        when(qrCodeService.generateQrCodeBase64(anyString(), anyInt(), anyInt())).thenReturn("AAAA");
        when(settingsService.getLabelSheetSize()).thenReturn("THERMAL_CUSTOM");
        when(settingsService.getCustomLabelWidth()).thenReturn(75);
        when(settingsService.getCustomLabelHeight()).thenReturn(75);
        when(settingsService.getPrintServerUrl()).thenReturn("http://192.168.1.53:8080");
        when(settingsService.isHideServerPrintOnWorkstation()).thenReturn(true);
        when(settingsService.isHideClientPrintOnRemote()).thenReturn(true);
        when(printServerService.checkConnectivity()).thenReturn(
                new PrintServerStatusResponse(true, "http://192.168.1.53:8080", "Print server is online"));

        mockMvc.perform(get("/batches/5/print"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/print"))
                .andExpect(model().attribute("printServerAvailable", true))
                .andExpect(model().attribute("hideServerPrint", false))
                .andExpect(model().attribute("hideClientPrint", true))
                .andExpect(content().string(containsString("id=\"serverPrintBtn\"")))
                .andExpect(content().string(containsString("id=\"clientPrintBtn\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void printLabelsView_WhenClientIsSameMachineAsPrintServer_ShouldHideServerPrintControlsAndEnableClientPrint() throws Exception {
        HarvestBatch batch = sampleBatch();
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(unitRepository.findByBatchId(5L)).thenReturn(sampleUnits(batch));
        when(qrCodeService.generateQrCodeBase64(anyString(), anyInt(), anyInt())).thenReturn("AAAA");
        when(settingsService.getLabelSheetSize()).thenReturn("THERMAL_CUSTOM");
        when(settingsService.getCustomLabelWidth()).thenReturn(75);
        when(settingsService.getCustomLabelHeight()).thenReturn(75);
        when(settingsService.getPrintServerUrl()).thenReturn("http://192.168.1.53:8080");
        when(settingsService.isHideServerPrintOnWorkstation()).thenReturn(true);
        when(settingsService.isHideClientPrintOnRemote()).thenReturn(true);
        when(printServerService.isClientSameAsPrintServer("192.168.1.53")).thenReturn(true);

        mockMvc.perform(get("/batches/5/print").with(req -> {
                    req.setRemoteAddr("192.168.1.53");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/print"))
                .andExpect(model().attribute("sameMachineAsPrintServer", true))
                .andExpect(model().attribute("printServerAvailable", false))
                .andExpect(model().attribute("hideServerPrint", true))
                .andExpect(model().attribute("hideClientPrint", false))
                .andExpect(content().string(containsString("d-none")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void printServerStatus_ShouldReturnJsonStatus() throws Exception {
        when(printServerService.checkConnectivity()).thenReturn(
                new PrintServerStatusResponse(false, "http://192.168.1.53:8080", "Print server unreachable"));

        mockMvc.perform(get("/batches/print-server/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.printServerUrl").value("http://192.168.1.53:8080"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void serverPrint_WhenSuccessful_ShouldReturn200Json() throws Exception {
        HarvestBatch batch = sampleBatch();
        List<InventoryUnit> units = sampleUnits(batch);
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(unitRepository.findByBatchId(5L)).thenReturn(units);
        when(printServerService.printBatchLabels(eq(batch), argThat(list -> list.size() == 2)))
                .thenReturn(new ServerPrintResponse(true, 5L, 2, 4096, "Successfully sent 2 label(s) to print server."));

        mockMvc.perform(post("/batches/5/server-print").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unitsPrinted").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void serverPrint_WithUnitIdFilter_ShouldFilterUnits() throws Exception {
        HarvestBatch batch = sampleBatch();
        List<InventoryUnit> units = sampleUnits(batch);
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));
        when(unitRepository.findByBatchId(5L)).thenReturn(units);
        when(printServerService.printBatchLabels(eq(batch), argThat(list -> list.size() == 1 && list.get(0).getId().equals(101L))))
                .thenReturn(new ServerPrintResponse(true, 5L, 1, 2048, "Successfully sent 1 label(s) to print server."));

        mockMvc.perform(post("/batches/5/server-print")
                        .param("unitId", "101")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unitsPrinted").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSettings_AsAdmin_ShouldDisplayAndSavePrintServerUrl() throws Exception {
        when(appSettingRepository.findAll()).thenReturn(List.of());
        when(settingsService.getPrintServerUrl()).thenReturn("http://192.168.1.53:8080");
        when(appSettingRepository.findBySettingKey(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(content().string(containsString("name=\"print_server_url\"")));

        mockMvc.perform(post("/admin/settings")
                        .param("print_server_url", "http://192.168.1.99:8080")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));

        verify(appSettingRepository).save(argThat(s ->
                "print_server_url".equals(s.getSettingKey()) && "http://192.168.1.99:8080".equals(s.getSettingValue())));
    }

    @Test
    @WithMockUser(roles = "SALES")
    void adminSettings_AsNonAdmin_ShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Access Denied")));
    }
}
