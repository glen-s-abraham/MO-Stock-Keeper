package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.dto.PrintServerStatusResponse;
import com.mushroom.stockkeeper.dto.ServerPrintResponse;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.NutritionLineItem;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PrintServerServiceTest {

    private AppSettingRepository appSettingRepository;
    private SettingsService settingsService;
    private PrintServerService printServerService;
    private HttpServer mockPrintServer;

    @BeforeEach
    void setUp() {
        appSettingRepository = Mockito.mock(AppSettingRepository.class);
        when(appSettingRepository.findBySettingKey(Mockito.anyString())).thenReturn(Optional.empty());
        settingsService = new SettingsService(appSettingRepository);
        printServerService = new PrintServerService(settingsService);
    }

    @AfterEach
    void tearDown() {
        if (mockPrintServer != null) {
            mockPrintServer.stop(0);
        }
    }

    private HarvestBatch createSampleBatch(boolean withNutrition) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Organic Oyster Mushroom");
        product.setFssaiLicenseNumber("12345678901234");
        product.setNetWeight(new BigDecimal("200"));
        product.setNetWeightUom("g");
        product.setMrp(new BigDecimal("120.00"));
        product.setStorageInstructions("STORE AT 4C - 8C");
        product.setHasNutritionValues(withNutrition);
        product.setNutritionBaseUnitValue(new BigDecimal("100"));
        product.setNutritionBaseUnitType("g");

        if (withNutrition) {
            List<NutritionLineItem> items = new ArrayList<>();
            NutritionLineItem item1 = new NutritionLineItem();
            item1.setComponentName("Energy");
            item1.setAmount(new BigDecimal("33"));
            item1.setMeasurementUnit("kcal");
            items.add(item1);

            NutritionLineItem item2 = new NutritionLineItem();
            item2.setComponentName("Protein");
            item2.setAmount(new BigDecimal("3.3"));
            item2.setMeasurementUnit("g");
            items.add(item2);
            product.setNutritionLineItems(items);
        }

        HarvestBatch batch = new HarvestBatch();
        batch.setId(10L);
        batch.setBatchCode("B-20261020-001");
        batch.setBatchDate(LocalDate.of(2026, 10, 20));
        batch.setExpiryDate(LocalDate.of(2026, 10, 27));
        batch.setProduct(product);
        return batch;
    }

    private List<InventoryUnit> createUnits(HarvestBatch batch, int count) {
        List<InventoryUnit> units = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            InventoryUnit unit = new InventoryUnit();
            unit.setId((long) i);
            unit.setUuid(batch.getBatchCode() + "-" + String.format("%03d", i));
            unit.setBatch(batch);
            units.add(unit);
        }
        return units;
    }

    @Test
    void generateBatchLabelPdf_Thermal75x75WithNutrition_ShouldIncludeNutritionTableAndCorrectPageSize() throws IOException {
        HarvestBatch batch = createSampleBatch(true);
        List<InventoryUnit> units = createUnits(batch, 3);

        byte[] pdfBytes = printServerService.generateBatchLabelsPdf(
                batch, units, "THERMAL_CUSTOM", 75, 75,
                "Marias Organics", "+91-9876543210", "12 Farm Road", "Care Center");

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(3, doc.getNumberOfPages());
            PDRectangle box = doc.getPage(0).getMediaBox();
            float expectedPt = 75f * 72f / 25.4f;
            assertEquals(expectedPt, box.getWidth(), 1.5f);
            assertEquals(expectedPt, box.getHeight(), 1.5f);

            String text = new PDFTextStripper().getText(doc);
            String normalized = text.replaceAll("\\s+", " ");
            String compact = text.replaceAll("\\s+", "");
            assertTrue(normalized.contains("Organic Oyster Mushroom"), "Text was: " + text);
            assertTrue(compact.contains("12345678901234"), "Text was: " + text);
            assertTrue(compact.contains("B-20261020-001"), "Text was: " + text);
            assertTrue(normalized.contains("NUTRITIONAL INFORMATION"), "Text was: " + text);
            assertTrue(normalized.contains("Energy"), "Text was: " + text);
            assertTrue(normalized.contains("STORE AT 4C - 8C"), "Text was: " + text);
        }
    }

    @Test
    void generateBatchLabelPdf_Thermal50x50Under75mm_ShouldOmitNutritionTable() throws IOException {
        HarvestBatch batch = createSampleBatch(true);
        List<InventoryUnit> units = createUnits(batch, 2);

        byte[] pdfBytes = printServerService.generateBatchLabelsPdf(
                batch, units, "THERMAL_CUSTOM", 50, 50,
                "Marias Organics", "+91-9876543210", "12 Farm Road", "Care Center");

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            String normalizedText = text.replaceAll("\\s+", " ");
            String compactText = text.replaceAll("\\s+", "");
            assertTrue(normalizedText.contains("Organic Oyster Mushroom"), "Expected product name in: " + text);
            assertTrue(compactText.contains("B-20261020-001"), "Expected batch code in: " + text);
            assertFalse(normalizedText.contains("NUTRITIONAL INFORMATION"));
        }
    }

    @Test
    void checkConnectivity_WhenServerOnline_ShouldReturnAvailableTrue() throws IOException {
        mockPrintServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPrintServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("OK".getBytes());
            exchange.close();
        });
        mockPrintServer.start();
        int port = mockPrintServer.getAddress().getPort();

        PrintServerStatusResponse status = printServerService.checkConnectivity("http://127.0.0.1:" + port);
        assertTrue(status.isAvailable());
        assertEquals("http://127.0.0.1:" + port, status.getPrintServerUrl());
    }

    @Test
    void checkConnectivity_WhenServerOfflineOrMalformed_ShouldReturnAvailableFalseWithinTimeout() {
        long start = System.currentTimeMillis();
        PrintServerStatusResponse status = printServerService.checkConnectivity("http://127.0.0.1:59999");
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(status.isAvailable());
        assertTrue(elapsed < 2500, "Connectivity check should respect strict timeout");

        PrintServerStatusResponse malformed = printServerService.checkConnectivity("not-a-valid-url");
        assertFalse(malformed.isAvailable());
    }

    @Test
    void printBatchLabels_WhenServerOnline_ShouldPostMultiPagePdfBytes() throws IOException {
        AtomicReference<byte[]> receivedPayload = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();

        mockPrintServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPrintServer.createContext("/print", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedPayload.set(exchange.getRequestBody().readAllBytes());
            byte[] resp = "{\"success\":true}".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        mockPrintServer.start();
        int port = mockPrintServer.getAddress().getPort();

        when(appSettingRepository.findBySettingKey(SettingsService.KEY_PRINT_SERVER_URL))
                .thenReturn(Optional.of(new com.mushroom.stockkeeper.model.AppSetting(
                        SettingsService.KEY_PRINT_SERVER_URL, "http://127.0.0.1:" + port)));
        when(appSettingRepository.findBySettingKey(SettingsService.KEY_LABEL_SHEET_SIZE))
                .thenReturn(Optional.of(new com.mushroom.stockkeeper.model.AppSetting(
                        SettingsService.KEY_LABEL_SHEET_SIZE, "THERMAL_CUSTOM")));
        when(appSettingRepository.findBySettingKey(SettingsService.KEY_CUSTOM_LABEL_WIDTH))
                .thenReturn(Optional.of(new com.mushroom.stockkeeper.model.AppSetting(
                        SettingsService.KEY_CUSTOM_LABEL_WIDTH, "75")));
        when(appSettingRepository.findBySettingKey(SettingsService.KEY_CUSTOM_LABEL_HEIGHT))
                .thenReturn(Optional.of(new com.mushroom.stockkeeper.model.AppSetting(
                        SettingsService.KEY_CUSTOM_LABEL_HEIGHT, "75")));

        HarvestBatch batch = createSampleBatch(true);
        List<InventoryUnit> units = createUnits(batch, 2);

        ServerPrintResponse response = printServerService.printBatchLabels(batch, units);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getUnitsPrinted());
        assertTrue(response.getBytesSent() > 0);
        assertEquals("application/pdf", receivedContentType.get());
        assertNotNull(receivedPayload.get());

        try (PDDocument doc = Loader.loadPDF(receivedPayload.get())) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    void generateBatchLabelPdf_ManualLabelsWithNullDates_ShouldRenderIndividualSequenceCodesAndWriteInPlaceholders() throws IOException {
        HarvestBatch manualBatch = createSampleBatch(true);
        manualBatch.setBatchCode("M-0001 to M-0003");
        manualBatch.setBatchDate(null);
        manualBatch.setExpiryDate(null);

        List<InventoryUnit> manualUnits = new ArrayList<>();
        for (int seq = 1; seq <= 3; seq++) {
            InventoryUnit unit = new InventoryUnit();
            unit.setId((long) seq);
            unit.setUuid(String.format("M-%04d", seq));
            unit.setBatch(manualBatch);
            manualUnits.add(unit);
        }

        byte[] pdfBytes = printServerService.generateBatchLabelsPdf(
                manualBatch, manualUnits, "THERMAL_CUSTOM", 75, 75,
                "Marias Organics", "+91-9876543210", "12 Farm Road", "Care Center");

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(3, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            String compact = text.replaceAll("\\s+", "");
            assertTrue(compact.contains("M-0001"), "Expected M-0001 in PDF text: " + text);
            assertTrue(compact.contains("M-0002"), "Expected M-0002 in PDF text: " + text);
            assertTrue(compact.contains("M-0003"), "Expected M-0003 in PDF text: " + text);
            assertTrue(compact.contains("Pkd:____________"), "Expected write-in Pkd placeholder in PDF text: " + text);
            assertTrue(compact.contains("Exp:____________"), "Expected write-in Exp placeholder in PDF text: " + text);
        }
    }

    @Test
    void allocateManualLabelSequence_ShouldRememberAndIncrementSequenceContinuously() {
        AtomicReference<String> storedVal = new AtomicReference<>("0");
        when(appSettingRepository.findBySettingKey(SettingsService.KEY_MANUAL_LABEL_LAST_SEQUENCE))
                .thenAnswer(inv -> Optional.of(new com.mushroom.stockkeeper.model.AppSetting(
                        SettingsService.KEY_MANUAL_LABEL_LAST_SEQUENCE, storedVal.get())));
        when(appSettingRepository.save(Mockito.any(com.mushroom.stockkeeper.model.AppSetting.class)))
                .thenAnswer(inv -> {
                    com.mushroom.stockkeeper.model.AppSetting saved = inv.getArgument(0);
                    if (SettingsService.KEY_MANUAL_LABEL_LAST_SEQUENCE.equals(saved.getSettingKey())) {
                        storedVal.set(saved.getSettingValue());
                    }
                    return saved;
                });

        com.mushroom.stockkeeper.dto.ManualLabelRange firstRun = settingsService.allocateManualLabelSequence(10);
        assertEquals(1, firstRun.getStartSeq());
        assertEquals(10, firstRun.getEndSeq());
        assertEquals("M-0001", firstRun.getStartCode());
        assertEquals("M-0010", firstRun.getEndCode());
        assertEquals(10, settingsService.getManualLabelLastSequence());

        com.mushroom.stockkeeper.dto.ManualLabelRange secondRun = settingsService.allocateManualLabelSequence(5);
        assertEquals(11, secondRun.getStartSeq());
        assertEquals(15, secondRun.getEndSeq());
        assertEquals("M-0011", secondRun.getStartCode());
        assertEquals("M-0015", secondRun.getEndCode());
        assertEquals(15, settingsService.getManualLabelLastSequence());
    }
}
