package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.ManualLabelRange;
import com.mushroom.stockkeeper.dto.PrintServerStatusResponse;
import com.mushroom.stockkeeper.dto.ServerPrintResponse;
import com.mushroom.stockkeeper.model.Product;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ManualLabelPrintControllerTest {

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

    private Product sampleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setName("Organic Oyster Mushroom");
        p.setFssaiLicenseNumber("12345678901234");
        p.setNetWeight(new BigDecimal("200"));
        p.setNetWeightUom("g");
        p.setMrp(new BigDecimal("120.00"));
        p.setStorageInstructions("STORE AT 4C - 8C");
        p.setHasNutritionValues(false);
        return p;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void manualLabelsForm_ShouldDisplayRememberedSequenceAndNextBatchCode() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(settingsService.getManualLabelLastSequence()).thenReturn(10);

        mockMvc.perform(get("/batches/manual-labels"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/manual-labels"))
                .andExpect(model().attribute("lastSequence", 10))
                .andExpect(model().attribute("nextSequence", 11))
                .andExpect(model().attribute("nextBatchCode", "M-0011"))
                .andExpect(content().string(containsString("M-0011")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateManualLabels_ShouldAdvanceSequenceAndRedirectToPrintWithoutCreatingStock() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(settingsService.allocateManualLabelSequence(5)).thenReturn(new ManualLabelRange(11, 15));

        mockMvc.perform(post("/batches/manual-labels/generate")
                        .param("productId", "1")
                        .param("quantity", "5")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/batches/manual-labels/print?productId=1&seqStart=11&seqEnd=15"));

        verify(settingsService).allocateManualLabelSequence(5);
        verify(batchRepository, never()).save(any());
        verify(unitRepository, never()).saveAll(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void printManualLabels_ShouldRenderSequentialBatchCodesAndBlankDatePlaceholders() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(settingsService.getLabelSheetSize()).thenReturn("THERMAL_CUSTOM");
        when(settingsService.getCustomLabelWidth()).thenReturn(75);
        when(settingsService.getCustomLabelHeight()).thenReturn(75);
        when(settingsService.getPrintServerUrl()).thenReturn("http://192.168.1.53:8080");
        when(settingsService.isHideServerPrintOnWorkstation()).thenReturn(true);
        when(settingsService.isHideClientPrintOnRemote()).thenReturn(true);
        when(printServerService.checkConnectivity()).thenReturn(
                new PrintServerStatusResponse(true, "http://192.168.1.53:8080", "Print server is online"));

        mockMvc.perform(get("/batches/manual-labels/print")
                        .param("productId", "1")
                        .param("seqStart", "1")
                        .param("seqEnd", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/print"))
                .andExpect(model().attribute("manualMode", true))
                .andExpect(model().attribute("backUrl", "/batches/manual-labels"))
                .andExpect(model().attribute("serverPrintUrl", "/batches/manual-labels/server-print"))
                .andExpect(content().string(containsString("M-0001")))
                .andExpect(content().string(containsString("M-0002")))
                .andExpect(content().string(containsString("M-0003")))
                .andExpect(content().string(containsString("____________")));

        verify(settingsService, never()).allocateManualLabelSequence(any(Integer.class));
        verify(batchRepository, never()).save(any());
        verify(unitRepository, never()).saveAll(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void serverPrintManualLabels_ShouldSendTransientManualUnitsToPrintServer() throws Exception {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(printServerService.printBatchLabels(
                argThat(b -> b != null && b.getBatchDate() == null && b.getExpiryDate() == null),
                argThat(units -> units != null && units.size() == 2
                        && "M-0005".equals(units.get(0).getUuid())
                        && "M-0006".equals(units.get(1).getUuid()))))
                .thenReturn(new ServerPrintResponse(true, 0L, 2, 4096, "Successfully sent 2 label(s) to print server."));

        mockMvc.perform(post("/batches/manual-labels/server-print")
                        .param("productId", "1")
                        .param("seqStart", "5")
                        .param("seqEnd", "6")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unitsPrinted").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateManualLabelSequence_ShouldUpdateLastSequenceCounter() throws Exception {
        mockMvc.perform(post("/batches/manual-labels/sequence")
                        .param("lastSequence", "50")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/batches/manual-labels"))
                .andExpect(flash().attributeExists("success"));

        verify(settingsService).setManualLabelLastSequence(50);
    }
}
