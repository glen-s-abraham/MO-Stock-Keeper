package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.dto.PrintServerStatusResponse;
import com.mushroom.stockkeeper.dto.ServerPrintResponse;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.NutritionLineItem;
import com.mushroom.stockkeeper.model.Product;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PrintServerService {

    private static final Logger log = LoggerFactory.getLogger(PrintServerService.class);
    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final SettingsService settingsService;
    private SpringTemplateEngine templateEngine;

    @Value("${app.print-server.connect-timeout-ms:3000}")
    private int connectTimeoutMs = 3000;

    @Value("${app.print-server.read-timeout-ms:15000}")
    private int readTimeoutMs = 15000;

    public PrintServerService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Autowired(required = false)
    public void setTemplateEngine(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Checks connectivity to the configured LAN print server within a strict timeout.
     */
    public PrintServerStatusResponse checkConnectivity() {
        return checkConnectivity(settingsService.getPrintServerUrl());
    }

    /**
     * Probes TCP/HTTP reachability of a given print server base URL within {@code connectTimeoutMs},
     * retrying once on timeout to handle USB Wi-Fi adapter power-save wakeup latency on the print workstation.
     */
    public PrintServerStatusResponse checkConnectivity(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return new PrintServerStatusResponse(false, "", "Print server URL is not configured");
        }

        String trimmedUrl = rawUrl.trim();
        try {
            URI uri = URI.create(trimmedUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) || host == null || host.isEmpty()) {
                return new PrintServerStatusResponse(false, trimmedUrl, "Invalid print server URL format");
            }

            int port = uri.getPort();
            if (port <= 0) {
                port = scheme.equalsIgnoreCase("https") ? 443 : 80;
            }

            Exception lastException = null;
            int maxAttempts = connectTimeoutMs >= 1000 ? 2 : 1;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
                    return new PrintServerStatusResponse(true, trimmedUrl, "Print server is online (" + host + ":" + port + ")");
                } catch (Exception connectEx) {
                    lastException = connectEx;
                }
            }

            String errMsg = lastException != null ? lastException.getMessage() : "Connection timed out";
            log.debug("Print server connectivity check failed for {}: {}", trimmedUrl, errMsg);
            return new PrintServerStatusResponse(false, trimmedUrl, "Print server unreachable (" + errMsg + ")");
        } catch (IllegalArgumentException e) {
            return new PrintServerStatusResponse(false, trimmedUrl, "Invalid print server URL: " + e.getMessage());
        } catch (Exception e) {
            log.debug("Print server connectivity check failed for {}: {}", trimmedUrl, e.getMessage());
            return new PrintServerStatusResponse(false, trimmedUrl, "Print server unreachable (" + e.getMessage() + ")");
        }
    }

    /**
     * Determines whether the requesting HTTP client IP is the same workstation that hosts the configured print server.
     */
    public boolean isClientSameAsPrintServer(String clientRemoteAddr) {
        if (clientRemoteAddr == null || clientRemoteAddr.trim().isEmpty()) {
            return false;
        }
        String rawUrl = settingsService.getPrintServerUrl();
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String printServerHost = uri.getHost();
            if (printServerHost == null || printServerHost.isEmpty()) {
                return false;
            }
            String cleanClientIp = clientRemoteAddr.trim();
            // Handle IPv6 zone index if present (e.g., fe80::1%eth0)
            int pctIdx = cleanClientIp.indexOf('%');
            if (pctIdx > 0) {
                cleanClientIp = cleanClientIp.substring(0, pctIdx);
            }

            if (cleanClientIp.equalsIgnoreCase(printServerHost)) {
                return true;
            }

            java.net.InetAddress clientAddr = java.net.InetAddress.getByName(cleanClientIp);
            java.net.InetAddress serverAddr = java.net.InetAddress.getByName(printServerHost);
            if (clientAddr.equals(serverAddr)) {
                return true;
            }
            if (clientAddr.isLoopbackAddress() && serverAddr.isLoopbackAddress()) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Could not compare client IP {} with print server host: {}", clientRemoteAddr, e.getMessage());
        }
        return false;
    }

    /**
     * Generates the multi-page PDF replicating the current print.html layout and dispatches it to the LAN print server.
     */
    public ServerPrintResponse printBatchLabels(HarvestBatch batch, List<InventoryUnit> units) {
        if (batch == null) {
            return new ServerPrintResponse(false, null, 0, 0, "Batch not found");
        }
        if (units == null || units.isEmpty()) {
            return new ServerPrintResponse(false, batch.getId(), 0, 0, "No inventory units selected to print");
        }

        String printServerUrl = settingsService.getPrintServerUrl();
        PrintServerStatusResponse connectivity = checkConnectivity(printServerUrl);
        if (!connectivity.isAvailable()) {
            return new ServerPrintResponse(false, batch.getId(), 0, 0,
                    "Print server is offline or unreachable at " + printServerUrl);
        }

        String labelSheetSize = settingsService.getLabelSheetSize();
        int customWidthMm = settingsService.getCustomLabelWidth() != null ? settingsService.getCustomLabelWidth() : 100;
        int customHeightMm = settingsService.getCustomLabelHeight() != null ? settingsService.getCustomLabelHeight() : 150;

        try {
            byte[] pdfBytes = generateBatchLabelsPdf(
                    batch,
                    units,
                    labelSheetSize,
                    customWidthMm,
                    customHeightMm,
                    settingsService.getCompanyName(),
                    settingsService.getContactNumber(),
                    settingsService.getRegisteredOfficeAddress(),
                    settingsService.getCustomerCareAddress());

            URI targetEndpoint = resolvePrintEndpointUri(printServerUrl);
            int[] resolvedDims = resolveLabelDimensionsMm(labelSheetSize, customWidthMm, customHeightMm);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetEndpoint)
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/pdf")
                    .header("X-Print-Format", "PDF")
                    .header("X-Label-Sheet-Size", labelSheetSize != null ? labelSheetSize : "A4_40")
                    .header("X-Label-Width-Mm", String.valueOf(resolvedDims[0]))
                    .header("X-Label-Height-Mm", String.valueOf(resolvedDims[1]))
                    .header("X-Label-Count", String.valueOf(units.size()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(pdfBytes))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ServerPrintResponse(
                        true,
                        batch.getId(),
                        units.size(),
                        pdfBytes.length,
                        "Successfully sent " + units.size() + " label(s) to print server.");
            } else {
                return new ServerPrintResponse(
                        false,
                        batch.getId(),
                        0,
                        pdfBytes.length,
                        "Print server returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ServerPrintResponse(false, batch.getId(), 0, 0, "Print job interrupted: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute server-side print for batch {}", batch.getId(), e);
            return new ServerPrintResponse(false, batch.getId(), 0, 0, "Failed to send print job: " + e.getMessage());
        }
    }

    private URI resolvePrintEndpointUri(String baseUrl) {
        String clean = baseUrl.trim();
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        URI uri = URI.create(clean);
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return URI.create(clean + "/print");
        }
        return uri;
    }

    private int[] resolveLabelDimensionsMm(String labelSheetSize, int customWidthMm, int customHeightMm) {
        String sheet = labelSheetSize != null ? labelSheetSize : "A4_40";
        return switch (sheet) {
            case "THERMAL_4x6" -> new int[]{100, 150};
            case "THERMAL_2x1" -> new int[]{50, 25};
            case "THERMAL_CUSTOM" -> new int[]{Math.max(20, customWidthMm), Math.max(15, customHeightMm)};
            case "A4_24", "A4_40" -> new int[]{210, 297};
            default -> new int[]{210, 297};
        };
    }

    /**
     * Generates a multi-page PDF document that is 100% head-to-head identical to the browser's window.print() output
     * by rendering the exact Thymeleaf print.html template + Bootstrap CSS + autoFitLabels() JS in Chrome
     * (either on the Windows print server workstation via /render-pdf or locally via headless Chrome),
     * with an automatic fallback to native PDFBox rendering if headless Chrome is unavailable.
     */
    public byte[] generateBatchLabelsPdf(
            HarvestBatch batch,
            List<InventoryUnit> units,
            String labelSheetSize,
            int customWidthMm,
            int customHeightMm,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress) throws IOException {

        byte[] remoteChromePdf = tryGeneratePdfViaPrintServerChrome(
                batch, units, labelSheetSize, customWidthMm, customHeightMm,
                companyName, contactNumber, registeredOfficeAddress, customerCareAddress);
        if (remoteChromePdf != null && remoteChromePdf.length > 100) {
            return remoteChromePdf;
        }

        byte[] chromePdf = tryGeneratePdfViaHeadlessChrome(
                batch, units, labelSheetSize, customWidthMm, customHeightMm,
                companyName, contactNumber, registeredOfficeAddress, customerCareAddress);
        if (chromePdf != null && chromePdf.length > 0) {
            return chromePdf;
        }

        return generateBatchLabelsPdfFallback(
                batch, units, labelSheetSize, customWidthMm, customHeightMm,
                companyName, contactNumber, registeredOfficeAddress, customerCareAddress);
    }

    private byte[] tryGeneratePdfViaPrintServerChrome(
            HarvestBatch batch,
            List<InventoryUnit> units,
            String labelSheetSize,
            int customWidthMm,
            int customHeightMm,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress) {
        try {
            String rawServerUrl = settingsService.getPrintServerUrl();
            if (rawServerUrl == null || rawServerUrl.trim().isEmpty()) {
                return null;
            }
            String cleanBase = rawServerUrl.trim();
            if (cleanBase.endsWith("/")) {
                cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
            }
            if (cleanBase.endsWith("/print")) {
                cleanBase = cleanBase.substring(0, cleanBase.length() - "/print".length());
            }

            String sheet = labelSheetSize != null ? labelSheetSize : "A4_40";
            int[] dimsMm = resolveLabelDimensionsMm(sheet, customWidthMm, customHeightMm);

            String htmlContent = buildStandalonePrintHtml(
                    batch, units, sheet, dimsMm[0], dimsMm[1],
                    companyName, contactNumber, registeredOfficeAddress, customerCareAddress,
                    null);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cleanBase + "/render-pdf"))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "text/html; charset=utf-8")
                    .header("X-Label-Width-Mm", String.valueOf(dimsMm[0]))
                    .header("X-Label-Height-Mm", String.valueOf(dimsMm[1]))
                    .POST(HttpRequest.BodyPublishers.ofString(htmlContent, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 100
                    && resp.body()[0] == 0x25 && resp.body()[1] == 0x50 && resp.body()[2] == 0x44 && resp.body()[3] == 0x46) {
                log.debug("Rendered 1:1 PDF via print workstation Chrome /render-pdf ({} bytes)", resp.body().length);
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("Print server /render-pdf not used ({}), trying local headless Chrome", e.getMessage());
        }
        return null;
    }

    private byte[] tryGeneratePdfViaHeadlessChrome(
            HarvestBatch batch,
            List<InventoryUnit> units,
            String labelSheetSize,
            int customWidthMm,
            int customHeightMm,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress) {

        String chromeBinary = findChromeBinary();
        if (chromeBinary == null) {
            return null;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("mo-label-print-");
            Path cssFile = tempDir.resolve("bootstrap.min.css");
            try (InputStream cssIn = new ClassPathResource("static/css/bootstrap.min.css").getInputStream()) {
                Files.copy(cssIn, cssFile);
            }

            String sheet = labelSheetSize != null ? labelSheetSize : "A4_40";
            int[] dimsMm = resolveLabelDimensionsMm(sheet, customWidthMm, customHeightMm);

            String htmlContent = buildStandalonePrintHtml(
                    batch, units, sheet, dimsMm[0], dimsMm[1],
                    companyName, contactNumber, registeredOfficeAddress, customerCareAddress,
                    cssFile.toUri().toString());

            Path htmlFile = tempDir.resolve("print.html");
            Path pdfFile = tempDir.resolve("labels.pdf");
            Files.writeString(htmlFile, htmlContent, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    chromeBinary,
                    "--headless",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--run-all-compositor-stages-before-draw",
                    "--virtual-time-budget=1500",
                    "--no-pdf-header-footer",
                    "--print-to-pdf=" + pdfFile.toAbsolutePath(),
                    htmlFile.toUri().toString()
            );
            // Clean snap library overrides if running inside VS Code / Snap terminal
            pb.environment().remove("LD_LIBRARY_PATH");
            pb.environment().remove("GTK_PATH");
            pb.environment().remove("GIO_MODULE_DIR");
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                log.warn("Headless Chrome PDF generation timed out, falling back to PDFBox");
                return null;
            }

            if (Files.exists(pdfFile) && Files.size(pdfFile) > 100) {
                byte[] bytes = Files.readAllBytes(pdfFile);
                log.debug("Generated 1:1 exact multi-page PDF via Headless Chrome ({} bytes)", bytes.length);
                return bytes;
            }
        } catch (Exception e) {
            log.warn("Headless Chrome PDF generation failed ({}), falling back to PDFBox", e.getMessage());
        } finally {
            if (tempDir != null) {
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                            });
                } catch (IOException ignored) { }
            }
        }
        return null;
    }

    private String findChromeBinary() {
        String[] candidates = {
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/snap/bin/chromium"
        };
        for (String candidate : candidates) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Produces the exact HTML/CSS/JS document directly from classpath:templates/batches/print.html
     * with inline bootstrap.min.css so remote printing never drifts from direct browser printing.
     */
    private String buildStandalonePrintHtml(
            HarvestBatch batch,
            List<InventoryUnit> units,
            String labelSheetSize,
            int widthMm,
            int heightMm,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress,
            String bootstrapCssUri) {

        String inlineBootstrapCss = "";
        try (InputStream cssIn = new ClassPathResource("static/css/bootstrap.min.css").getInputStream()) {
            inlineBootstrapCss = new String(cssIn.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        // Render the live classpath:templates/batches/print.html template via StringTemplateResolver
        // so that any template changes in batches/print.html are automatically reflected in remote print.
        try (InputStream tplIn = new ClassPathResource("templates/batches/print.html").getInputStream()) {
            String rawTemplate = new String(tplIn.readAllBytes(), StandardCharsets.UTF_8);

            // Strip the second script block (status polling / server print AJAX) which uses @{...} URL expressions
            int statusScriptIdx = rawTemplate.indexOf("<script th:inline=\"javascript\">");
            if (statusScriptIdx > 0) {
                int endBodyIdx = rawTemplate.indexOf("</body>", statusScriptIdx);
                if (endBodyIdx > statusScriptIdx) {
                    rawTemplate = rawTemplate.substring(0, statusScriptIdx) + rawTemplate.substring(endBodyIdx);
                }
            }

            // Replace context-relative @{...} attributes so standalone Thymeleaf evaluation does not require IWebContext
            rawTemplate = rawTemplate
                    .replace("th:href=\"@{/favicon.svg}\"", "href=\"\"")
                    .replace("<link th:href=\"@{/css/bootstrap.min.css}\" rel=\"stylesheet\">",
                            "<style>" + inlineBootstrapCss + "</style>")
                    .replaceAll("th:href=\"@\\{[^\\\"]*\\}\"", "href=\"#\"");

            SpringTemplateEngine standaloneEngine = new SpringTemplateEngine();
            org.thymeleaf.templateresolver.StringTemplateResolver stringResolver =
                    new org.thymeleaf.templateresolver.StringTemplateResolver();
            stringResolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
            stringResolver.setCacheable(false);
            standaloneEngine.setTemplateResolver(stringResolver);

            Context ctx = new Context();
            Map<Long, String> qrCodes = new HashMap<>();
            for (InventoryUnit u : units) {
                if (u.getId() != null) {
                    qrCodes.put(u.getId(), "");
                }
            }
            ctx.setVariable("batch", batch);
            ctx.setVariable("units", units);
            ctx.setVariable("qrCodes", qrCodes);
            ctx.setVariable("companyName", companyName);
            ctx.setVariable("contactNumber", contactNumber);
            ctx.setVariable("registeredOfficeAddress", registeredOfficeAddress);
            ctx.setVariable("customerCareAddress", customerCareAddress);
            ctx.setVariable("labelSheetSize", labelSheetSize);
            ctx.setVariable("customLabelWidth", widthMm);
            ctx.setVariable("customLabelHeight", heightMm);
            ctx.setVariable("printServerAvailable", true);
            ctx.setVariable("sameMachineAsPrintServer", true);
            ctx.setVariable("hideServerPrintOnWorkstation", true);
            ctx.setVariable("hideClientPrintOnRemote", true);
            ctx.setVariable("hideServerPrint", true);
            ctx.setVariable("hideClientPrint", true);
            ctx.setVariable("printServerUrl", "");
            ctx.setVariable("printServerMessage", "");

            String rendered = standaloneEngine.process(rawTemplate, ctx);
            String pageSizeCss = "<style>@page { size: " + widthMm + "mm " + heightMm + "mm !important; margin: 0 !important; } "
                    + "html, body { margin: 0 !important; padding: 0 !important; -webkit-print-color-adjust: exact; print-color-adjust: exact; } "
                    + ".container-fluid { padding: 0 !important; margin: 0 !important; } "
                    + ".no-print { display: none !important; }</style></head>";
            return rendered.replace("</head>", pageSizeCss);
        } catch (Exception e) {
            log.warn("Live batches/print.html template render failed ({}), using standalone replica HTML", e.getMessage());
        }

        Product product = batch != null ? batch.getProduct() : null;
        boolean showNutrition = shouldShowNutrition(product, labelSheetSize, widthMm, heightMm);

        StringBuilder sb = new StringBuilder(16384);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        if (!inlineBootstrapCss.isEmpty()) {
            sb.append("<style>").append(inlineBootstrapCss).append("</style>");
        } else if (bootstrapCssUri != null) {
            sb.append("<link href=\"").append(bootstrapCssUri).append("\" rel=\"stylesheet\">");
        }
        sb.append("<style>\n");
        sb.append("@page { size: ").append(widthMm).append("mm ").append(heightMm).append("mm; margin: 0; }\n");
        sb.append("html, body { margin: 0; padding: 0; background: white; -webkit-print-color-adjust: exact; print-color-adjust: exact; }\n");
        sb.append(".container-fluid { padding: 0 !important; margin: 0 !important; }\n");
        sb.append(".label-grid { display: grid; gap: 0; padding: 0; }\n");
        sb.append(".label-item { border: 1px dashed #ccc; padding: 1px; text-align: center; page-break-inside: avoid; background: white; overflow: hidden; box-sizing: border-box; }\n");
        sb.append(".sheet-A4_24 .label-grid { grid-template-columns: repeat(3, 1fr); }\n");
        sb.append(".sheet-A4_40 .label-grid { grid-template-columns: repeat(4, 1fr); }\n");
        sb.append(".sheet-THERMAL_4x6 { width: 100mm; }\n");
        sb.append(".sheet-THERMAL_4x6 .label-grid { grid-template-columns: 1fr; gap: 0; padding: 0; }\n");
        sb.append(".sheet-THERMAL_4x6 .label-item { width: 100mm; height: 150mm; border: none; padding: 5mm; page-break-after: always; display: flex; flex-direction: column; justify-content: center; }\n");
        sb.append(".sheet-THERMAL_2x1 { width: 50mm; }\n");
        sb.append(".sheet-THERMAL_2x1 .label-grid { grid-template-columns: 1fr; gap: 0; padding: 0; }\n");
        sb.append(".sheet-THERMAL_2x1 .label-item { width: 50mm; height: 25mm; border: none; padding: 1mm; page-break-after: always; display: flex; flex-direction: column; justify-content: center; }\n");
        sb.append(".sheet-THERMAL_2x1 .label-item .fw-bold { font-size: 6pt !important; }\n");
        sb.append(".sheet-THERMAL_2x1 .label-item div { font-size: 5pt !important; line-height: 1.1; }\n");
        sb.append(".sheet-THERMAL_CUSTOM { width: ").append(widthMm).append("mm; }\n");
        sb.append(".sheet-THERMAL_CUSTOM .label-grid { grid-template-columns: 1fr; gap: 0; padding: 0; }\n");
        sb.append(".sheet-THERMAL_CUSTOM .label-item { width: ").append(widthMm).append("mm; height: ").append(heightMm).append("mm; border: none; padding: 1mm; page-break-after: always; display: flex; flex-direction: column; justify-content: center; }\n");
        sb.append("</style></head>");
        sb.append("<body class=\"sheet-").append(escapeHtml(labelSheetSize)).append("\">");
        sb.append("<div class=\"container-fluid\"><div class=\"label-grid\">");

        String prodName = product != null && product.getName() != null ? escapeHtml(product.getName()) : "";
        String fssai = product != null && product.getFssaiLicenseNumber() != null && !product.getFssaiLicenseNumber().isEmpty()
                ? escapeHtml("FSSAI Lic No: " + product.getFssaiLicenseNumber()) : null;
        String compNameHtml = escapeHtml(defaultStr(companyName, ""));
        String regAddrHtml = escapeHtml(defaultStr(registeredOfficeAddress, ""));
        String careAddrHtml = escapeHtml(defaultStr(customerCareAddress, ""));
        String phoneHtml = escapeHtml("Tel: " + defaultStr(contactNumber, ""));
        String batchCodeHtml = batch != null && batch.getBatchCode() != null ? escapeHtml(batch.getBatchCode()) : "";
        String pkdHtml = batch != null && batch.getBatchDate() != null ? escapeHtml(batch.getBatchDate().toString()) : "";
        String expHtml = batch != null && batch.getExpiryDate() != null ? escapeHtml(batch.getExpiryDate().toString()) : "";
        String netWtHtml = "";
        if (product != null && product.getNetWeight() != null) {
            netWtHtml = escapeHtml(product.getNetWeight().toString()
                    + (product.getNetWeightUom() != null ? product.getNetWeightUom() : ""));
        }
        String mrpHtml = (product != null && product.getMrp() != null)
                ? escapeHtml("MRP: ₹" + product.getMrp().toString() + " (Incl. taxes)") : null;
        String storageHtml = (product != null && product.getStorageInstructions() != null && !product.getStorageInstructions().isEmpty())
                ? escapeHtml(product.getStorageInstructions()) : null;

        for (int i = 0; i < units.size(); i++) {
            sb.append("<div class=\"label-item\">");
            sb.append("<div class=\"d-flex flex-column\" style=\"text-align: left; font-size: 14pt; line-height: 1.05;\">");

            // Top section: Veg symbol, Product Name, FSSAI
            sb.append("<div class=\"d-flex justify-content-between align-items-start w-100 mb-0\">");
            sb.append("<div class=\"border border-success d-flex justify-content-center align-items-center\" style=\"width: 10mm; height: 10mm; min-width: 10mm; margin: 1.5mm 1.5mm 4mm 1.5mm; border-width: 2px !important; -webkit-print-color-adjust: exact; print-color-adjust: exact;\">");
            sb.append("<div class=\"bg-success rounded-circle\" style=\"width: 5mm; height: 5mm; -webkit-print-color-adjust: exact; print-color-adjust: exact;\"></div>");
            sb.append("</div>");
            sb.append("<div class=\"text-center flex-grow-1 px-1\">");
            sb.append("<div class=\"fw-bold\" style=\"font-size: 13pt;\">").append(prodName).append("</div>");
            if (fssai != null) {
                sb.append("<div class=\"fw-bold\" style=\"font-size: 9pt;\">").append(fssai).append("</div>");
            }
            sb.append("</div></div>");

            // Company Info
            sb.append("<div class=\"row g-0 mb-0 border-top\" style=\"font-size: 9pt;\">");
            sb.append("<div class=\"col-6 pe-1\"><strong>GROWN &amp; PACKED BY:</strong><br>");
            sb.append("<span>").append(compNameHtml).append("</span><br>");
            sb.append("<span style=\"font-size: 8pt;\">").append(regAddrHtml).append("</span></div>");
            sb.append("<div class=\"col-6 ps-1 border-start\"><strong>CUSTOMER CARE:</strong><br>");
            sb.append("<span style=\"font-size: 8pt;\">").append(careAddrHtml).append("</span><br>");
            sb.append("<span class=\"fw-bold\" style=\"font-size: 10pt;\">").append(phoneHtml).append("</span></div>");
            sb.append("</div>");

            // Batch & Product Details
            sb.append("<div class=\"row g-0 mb-0 border-top border-bottom\">");
            sb.append("<div class=\"col-6 pe-1\">");
            sb.append("Batch No: <span class=\"fw-bold\">").append(batchCodeHtml).append("</span><br>");
            sb.append("Pkd: <span class=\"fw-bold\" style=\"font-size: 10pt;\">").append(pkdHtml).append("</span><br>");
            sb.append("Exp: <span class=\"fw-bold text-uppercase\" style=\"font-size: 10pt;\">").append(expHtml).append("</span>");
            sb.append("</div>");
            sb.append("<div class=\"col-6 border-start ps-1\">");
            sb.append("Net Wt: <span class=\"fw-bold\">").append(netWtHtml).append("</span><br>");
            if (mrpHtml != null) {
                sb.append("<span>").append(mrpHtml).append("</span>");
            }
            sb.append("</div></div>");

            // Nutrition Table
            if (showNutrition && product != null && product.getNutritionLineItems() != null) {
                String baseUnit = (product.getNutritionBaseUnitValue() != null ? product.getNutritionBaseUnitValue().toString() : "100")
                        + (product.getNutritionBaseUnitType() != null ? product.getNutritionBaseUnitType() : "g");
                sb.append("<div class=\"mt-1 w-100\">");
                sb.append("<table class=\"table table-sm table-bordered nutrition-table mb-0 w-100\" style=\"font-size: 11pt; border-color: #000;\">");
                sb.append("<thead><tr><th colspan=\"2\" class=\"text-center p-1 bg-light m-0 border-bottom-0\">NUTRITIONAL INFORMATION (Approx. per ").append(escapeHtml(baseUnit)).append(")</th></tr></thead>");
                sb.append("<tbody>");
                for (NutritionLineItem item : product.getNutritionLineItems()) {
                    sb.append("<tr>");
                    sb.append("<td class=\"p-1 m-0\" style=\"width: 60%; border-top: none;\">").append(escapeHtml(defaultStr(item.getComponentName(), ""))).append("</td>");
                    String amt = (item.getAmount() != null ? item.getAmount().toString() : "0") + defaultStr(item.getMeasurementUnit(), "");
                    sb.append("<td class=\"p-1 m-0 text-end\" style=\"width: 40%; border-top: none;\">").append(escapeHtml(amt)).append("</td>");
                    sb.append("</tr>");
                }
                sb.append("</tbody></table></div>");
            }

            // Storage Instructions
            if (storageHtml != null) {
                sb.append("<div class=\"border border-dark p-2 mt-1 text-center w-100\" style=\"font-size: 11pt; border-style: dashed !important; border-width: 2px !important; border-radius: 2px; overflow-wrap: break-word; word-wrap: break-word; box-sizing: border-box;\">")
                        .append(storageHtml).append("</div>");
            }

            sb.append("</div></div>");
        }

        sb.append("</div></div>");
        // Exact autoFitLabels() script from batches/print.html
        sb.append("<script>\n")
                .append("function autoFitLabels() {\n")
                .append("  const labels = document.querySelectorAll('.label-item');\n")
                .append("  labels.forEach(label => {\n")
                .append("    const content = label.querySelector('div');\n")
                .append("    if (content) {\n")
                .append("      content.style.transform = 'none';\n")
                .append("      content.style.marginLeft = '0';\n")
                .append("      content.style.marginTop = '0';\n")
                .append("      const style = window.getComputedStyle(label);\n")
                .append("      const paddingVertical = parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);\n")
                .append("      const paddingHorizontal = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);\n")
                .append("      label.style.justifyContent = 'flex-start';\n")
                .append("      label.style.alignItems = 'flex-start';\n")
                .append("      const availHeight = label.clientHeight - paddingVertical;\n")
                .append("      const availWidth = label.clientWidth - paddingHorizontal;\n")
                .append("      if (availHeight <= 0 || availWidth <= 0) return;\n")
                .append("      const targetRatio = availWidth / availHeight;\n")
                .append("      let minW = 10, maxW = 4000, bestW = availWidth;\n")
                .append("      for (let i = 0; i < 20; i++) {\n")
                .append("        let midW = (minW + maxW) / 2;\n")
                .append("        content.style.width = midW + 'px';\n")
                .append("        let currentW = content.scrollWidth;\n")
                .append("        let currentH = content.scrollHeight;\n")
                .append("        let ratio = currentW / currentH;\n")
                .append("        if (ratio > targetRatio) { maxW = midW; } else { minW = midW; }\n")
                .append("        bestW = midW;\n")
                .append("      }\n")
                .append("      content.style.width = bestW + 'px';\n")
                .append("      const finalW = content.scrollWidth;\n")
                .append("      const finalH = content.scrollHeight;\n")
                .append("      const scaleW = availWidth / finalW;\n")
                .append("      const scaleH = availHeight / finalH;\n")
                .append("      const scale = Math.min(scaleW, scaleH) * 0.95;\n")
                .append("      content.style.transformOrigin = 'top left';\n")
                .append("      content.style.transform = `scale(${scale})`;\n")
                .append("      const scaledW = finalW * scale;\n")
                .append("      const scaledH = finalH * scale;\n")
                .append("      content.style.marginLeft = ((availWidth - scaledW) / 2) + 'px';\n")
                .append("      content.style.marginTop = ((availHeight - scaledH) / 2) + 'px';\n")
                .append("    }\n")
                .append("  });\n")
                .append("}\n")
                .append("document.addEventListener('DOMContentLoaded', autoFitLabels);\n")
                .append("window.addEventListener('load', autoFitLabels);\n")
                .append("autoFitLabels();\n")
                .append("</script></body></html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private byte[] generateBatchLabelsPdfFallback(
            HarvestBatch batch,
            List<InventoryUnit> units,
            String labelSheetSize,
            int customWidthMm,
            int customHeightMm,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress) throws IOException {

        String sheet = labelSheetSize != null ? labelSheetSize : "A4_40";
        boolean showNutrition = shouldShowNutrition(batch != null ? batch.getProduct() : null, sheet, customWidthMm, customHeightMm);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            if ("A4_24".equals(sheet) || "A4_40".equals(sheet)) {
                int cols = "A4_24".equals(sheet) ? 3 : 4;
                int rows = "A4_24".equals(sheet) ? 8 : 10;
                int perPage = cols * rows;
                float pageW = 210f * POINTS_PER_MM;
                float pageH = 297f * POINTS_PER_MM;
                float cellW = pageW / cols;
                float cellH = pageH / rows;
                float safePad = 1.0f * POINTS_PER_MM;

                for (int i = 0; i < units.size(); i += perPage) {
                    PDPage page = new PDPage(new PDRectangle(pageW, pageH));
                    doc.addPage(page);
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        int end = Math.min(i + perPage, units.size());
                        for (int idx = i; idx < end; idx++) {
                            int slot = idx - i;
                            int r = slot / cols;
                            int c = slot % cols;
                            float cellX = c * cellW;
                            float cellTopY = pageH - (r * cellH);
                            float cellBottomY = cellTopY - cellH;

                            // Draw light dashed cell border for A4 sheet
                            cs.setStrokingColor(new Color(204, 204, 204));
                            cs.setLineWidth(1.0f);
                            cs.setLineDashPattern(new float[]{3f, 2f}, 0);
                            cs.addRect(cellX, cellBottomY, cellW, cellH);
                            cs.stroke();
                            cs.setLineDashPattern(new float[]{}, 0);

                            renderScaledLabelInBox(
                                    cs, fontRegular, fontBold,
                                    batch, companyName, contactNumber, registeredOfficeAddress, customerCareAddress,
                                    showNutrition,
                                    cellX + safePad, cellBottomY + safePad,
                                    cellW - (2 * safePad), cellH - (2 * safePad));
                        }
                    }
                }
            } else {
                int[] dimsMm = resolveLabelDimensionsMm(sheet, customWidthMm, customHeightMm);
                float pageW = dimsMm[0] * POINTS_PER_MM;
                float pageH = dimsMm[1] * POINTS_PER_MM;
                // Constitution Principle VI: minimum 1mm structural safe margin (5mm for THERMAL_4x6 as in print.html)
                float padMm = "THERMAL_4x6".equals(sheet) ? 5.0f : 1.0f;
                float padPt = padMm * POINTS_PER_MM;

                for (int i = 0; i < units.size(); i++) {
                    PDPage page = new PDPage(new PDRectangle(pageW, pageH));
                    doc.addPage(page);
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        renderScaledLabelInBox(
                                cs, fontRegular, fontBold,
                                batch, companyName, contactNumber, registeredOfficeAddress, customerCareAddress,
                                showNutrition,
                                padPt, padPt,
                                pageW - (2 * padPt), pageH - (2 * padPt));
                    }
                }
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private boolean shouldShowNutrition(Product product, String sheet, int customWidthMm, int customHeightMm) {
        if (product == null || !product.isHasNutritionValues()) {
            return false;
        }
        return ("THERMAL_CUSTOM".equals(sheet) && customWidthMm >= 75 && customHeightMm >= 75)
                || "THERMAL_4x6".equals(sheet);
    }

    /**
     * Replicates the binary-search virtual width and aspect-ratio bounding-box scaling from print.html.
     */
    private void renderScaledLabelInBox(
            PDPageContentStream cs,
            PDFont fontRegular,
            PDFont fontBold,
            HarvestBatch batch,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress,
            boolean showNutrition,
            float boxX,
            float boxY,
            float availWidth,
            float availHeight) throws IOException {

        if (availWidth <= 0 || availHeight <= 0 || batch == null) {
            return;
        }

        float targetRatio = availWidth / availHeight;
        float minW = 140f;
        float maxW = 1200f;
        float bestW = Math.max(availWidth, 220f);

        for (int iter = 0; iter < 15; iter++) {
            float midW = (minW + maxW) / 2f;
            float measuredH = measureVirtualLabelHeight(
                    fontRegular, fontBold, batch, companyName, contactNumber,
                    registeredOfficeAddress, customerCareAddress, showNutrition, midW);
            float ratio = midW / Math.max(1f, measuredH);
            if (ratio > targetRatio) {
                maxW = midW;
            } else {
                minW = midW;
            }
            bestW = midW;
        }

        float virtualW = bestW;
        float virtualH = measureVirtualLabelHeight(
                fontRegular, fontBold, batch, companyName, contactNumber,
                registeredOfficeAddress, customerCareAddress, showNutrition, virtualW);

        float scaleW = availWidth / virtualW;
        float scaleH = availHeight / Math.max(1f, virtualH);
        // 0.95 buffer matching autoFitLabels() in print.html
        float scale = Math.min(scaleW, scaleH) * 0.95f;

        float scaledW = virtualW * scale;
        float scaledH = virtualH * scale;
        float offsetX = boxX + (availWidth - scaledW) / 2f;
        float offsetY = boxY + (availHeight - scaledH) / 2f;

        cs.saveGraphicsState();
        cs.transform(new Matrix(scale, 0, 0, scale, offsetX, offsetY));
        drawVirtualLabelContent(
                cs, fontRegular, fontBold, batch, companyName, contactNumber,
                registeredOfficeAddress, customerCareAddress, showNutrition, virtualW, virtualH);
        cs.restoreGraphicsState();
    }

    private float measureVirtualLabelHeight(
            PDFont fontRegular,
            PDFont fontBold,
            HarvestBatch batch,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress,
            boolean showNutrition,
            float virtualW) throws IOException {

        Product product = batch.getProduct();
        String prodName = product != null && product.getName() != null ? sanitizeText(product.getName()) : "PRODUCT";
        String fssai = product != null && product.getFssaiLicenseNumber() != null && !product.getFssaiLicenseNumber().isEmpty()
                ? "FSSAI Lic No: " + sanitizeText(product.getFssaiLicenseNumber()) : null;

        float vegBoxPt = 10f * POINTS_PER_MM;
        float vegMarginSide = 1.5f * POINTS_PER_MM;
        float vegMarginBottom = 4.0f * POINTS_PER_MM;
        float vegTotalW = vegBoxPt + (2 * vegMarginSide);
        float vegTotalH = vegBoxPt + vegMarginSide + vegMarginBottom;

        float headerTextW = Math.max(60f, virtualW - vegTotalW - 8f);
        List<String> prodLines = wrapText(fontBold, 13f, prodName, headerTextW);
        List<String> fssaiLines = fssai != null ? wrapText(fontBold, 9f, fssai, headerTextW) : Collections.emptyList();
        float headerTextH = (prodLines.size() * 15f) + (fssaiLines.size() * 11f) + 4f;
        float topSectionH = Math.max(vegTotalH, headerTextH);

        float colW = (virtualW / 2f) - 6f;
        List<String> leftCompLines = new ArrayList<>();
        leftCompLines.addAll(wrapText(fontBold, 9f, "GROWN & PACKED BY:", colW));
        leftCompLines.addAll(wrapText(fontRegular, 9f, sanitizeText(defaultStr(companyName, "Marias Organics")), colW));
        leftCompLines.addAll(wrapText(fontRegular, 8f, sanitizeText(defaultStr(registeredOfficeAddress, "")), colW));

        List<String> rightCompLines = new ArrayList<>();
        rightCompLines.addAll(wrapText(fontBold, 9f, "CUSTOMER CARE:", colW));
        rightCompLines.addAll(wrapText(fontRegular, 8f, sanitizeText(defaultStr(customerCareAddress, "")), colW));
        rightCompLines.addAll(wrapText(fontBold, 10f, "Tel: " + sanitizeText(defaultStr(contactNumber, "")), colW));

        float compSectionH = Math.max(leftCompLines.size() * 11f, rightCompLines.size() * 11f) + 6f;

        float batchSectionH = (3 * 13f) + 6f;

        float nutritionSectionH = 0f;
        if (showNutrition && product != null && product.getNutritionLineItems() != null) {
            int itemCount = product.getNutritionLineItems().size();
            nutritionSectionH = 18f + (itemCount * 14f) + 6f;
        }

        float storageSectionH = 0f;
        if (product != null && product.getStorageInstructions() != null && !product.getStorageInstructions().trim().isEmpty()) {
            List<String> storageLines = wrapText(fontRegular, 11f, sanitizeText(product.getStorageInstructions()), virtualW - 12f);
            storageSectionH = (storageLines.size() * 13f) + 10f;
        }

        return topSectionH + compSectionH + batchSectionH + nutritionSectionH + storageSectionH;
    }

    private void drawVirtualLabelContent(
            PDPageContentStream cs,
            PDFont fontRegular,
            PDFont fontBold,
            HarvestBatch batch,
            String companyName,
            String contactNumber,
            String registeredOfficeAddress,
            String customerCareAddress,
            boolean showNutrition,
            float virtualW,
            float virtualH) throws IOException {

        Product product = batch.getProduct();
        float cursorY = virtualH;

        // 1. Top Section: 10mm x 10mm Veg Symbol Placeholder + Product Name & FSSAI
        float vegBoxPt = 10f * POINTS_PER_MM;
        float vegMarginSide = 1.5f * POINTS_PER_MM;
        float vegMarginBottom = 4.0f * POINTS_PER_MM;
        float vegTotalW = vegBoxPt + (2 * vegMarginSide);
        float vegTotalH = vegBoxPt + vegMarginSide + vegMarginBottom;

        float vegX = vegMarginSide;
        float vegY = cursorY - vegMarginSide - vegBoxPt;
        cs.setStrokingColor(new Color(25, 135, 84));
        cs.setLineWidth(2.0f);
        cs.addRect(vegX, vegY, vegBoxPt, vegBoxPt);
        cs.stroke();

        // Inner veg circle (5mm diameter)
        float circleRadius = (5f * POINTS_PER_MM) / 2f;
        drawFilledCircle(cs, vegX + (vegBoxPt / 2f), vegY + (vegBoxPt / 2f), circleRadius, new Color(25, 135, 84));

        cs.setStrokingColor(Color.BLACK);
        cs.setNonStrokingColor(Color.BLACK);

        String prodName = product != null && product.getName() != null ? sanitizeText(product.getName()) : "PRODUCT";
        String fssai = product != null && product.getFssaiLicenseNumber() != null && !product.getFssaiLicenseNumber().isEmpty()
                ? "FSSAI Lic No: " + sanitizeText(product.getFssaiLicenseNumber()) : null;

        float headerTextX = vegTotalW + 4f;
        float headerTextW = Math.max(60f, virtualW - vegTotalW - 8f);
        List<String> prodLines = wrapText(fontBold, 13f, prodName, headerTextW);
        List<String> fssaiLines = fssai != null ? wrapText(fontBold, 9f, fssai, headerTextW) : Collections.emptyList();

        float textY = cursorY - 14f;
        for (String line : prodLines) {
            drawCenteredText(cs, fontBold, 13f, line, headerTextX, headerTextW, textY);
            textY -= 15f;
        }
        for (String line : fssaiLines) {
            drawCenteredText(cs, fontBold, 9f, line, headerTextX, headerTextW, textY);
            textY -= 11f;
        }

        float headerTextH = (prodLines.size() * 15f) + (fssaiLines.size() * 11f) + 4f;
        float topSectionH = Math.max(vegTotalH, headerTextH);
        cursorY -= topSectionH;

        // Horizontal divider
        drawSolidLine(cs, 0, cursorY, virtualW, cursorY, 1.0f);

        // 2. Company Info Section (2 columns)
        float colW = (virtualW / 2f) - 6f;
        float midX = virtualW / 2f;
        float leftY = cursorY - 11f;
        for (String line : wrapText(fontBold, 9f, "GROWN & PACKED BY:", colW)) {
            drawLeftText(cs, fontBold, 9f, line, 2f, leftY);
            leftY -= 11f;
        }
        for (String line : wrapText(fontRegular, 9f, sanitizeText(defaultStr(companyName, "Marias Organics")), colW)) {
            drawLeftText(cs, fontRegular, 9f, line, 2f, leftY);
            leftY -= 11f;
        }
        for (String line : wrapText(fontRegular, 8f, sanitizeText(defaultStr(registeredOfficeAddress, "")), colW)) {
            drawLeftText(cs, fontRegular, 8f, line, 2f, leftY);
            leftY -= 10f;
        }

        float rightY = cursorY - 11f;
        for (String line : wrapText(fontBold, 9f, "CUSTOMER CARE:", colW)) {
            drawLeftText(cs, fontBold, 9f, line, midX + 4f, rightY);
            rightY -= 11f;
        }
        for (String line : wrapText(fontRegular, 8f, sanitizeText(defaultStr(customerCareAddress, "")), colW)) {
            drawLeftText(cs, fontRegular, 8f, line, midX + 4f, rightY);
            rightY -= 10f;
        }
        for (String line : wrapText(fontBold, 10f, "Tel: " + sanitizeText(defaultStr(contactNumber, "")), colW)) {
            drawLeftText(cs, fontBold, 10f, line, midX + 4f, rightY);
            rightY -= 11f;
        }

        float compBottomY = Math.min(leftY, rightY) - 2f;
        drawSolidLine(cs, midX, cursorY, midX, compBottomY, 1.0f);
        cursorY = compBottomY;

        // Horizontal divider
        drawSolidLine(cs, 0, cursorY, virtualW, cursorY, 1.0f);

        // 3. Batch & Product Details (2 columns)
        float batchTopY = cursorY;
        float bLeftY = cursorY - 12f;
        drawLeftText(cs, fontBold, 10f, "Batch No: " + sanitizeText(defaultStr(batch.getBatchCode(), "")), 2f, bLeftY);
        bLeftY -= 13f;
        drawLeftText(cs, fontBold, 10f, "Pkd: " + (batch.getBatchDate() != null ? batch.getBatchDate().toString() : ""), 2f, bLeftY);
        bLeftY -= 13f;
        drawLeftText(cs, fontBold, 10f, "Exp: " + (batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : ""), 2f, bLeftY);
        bLeftY -= 4f;

        float bRightY = cursorY - 12f;
        String netWt = "";
        if (product != null && product.getNetWeight() != null) {
            netWt = product.getNetWeight().stripTrailingZeros().toPlainString()
                    + (product.getNetWeightUom() != null ? product.getNetWeightUom() : "");
        }
        drawLeftText(cs, fontBold, 10f, "Net Wt: " + sanitizeText(netWt), midX + 4f, bRightY);
        bRightY -= 13f;
        if (product != null && product.getMrp() != null) {
            drawLeftText(cs, fontRegular, 10f, "MRP: Rs." + product.getMrp().toPlainString() + " (Incl. taxes)", midX + 4f, bRightY);
        }

        float batchBottomY = bLeftY;
        drawSolidLine(cs, midX, batchTopY, midX, batchBottomY, 1.0f);
        drawSolidLine(cs, 0, batchBottomY, virtualW, batchBottomY, 1.0f);
        cursorY = batchBottomY - 4f;

        // 4. Conditional Nutrition Table (>= 75mm and hasNutritionValues)
        if (showNutrition && product != null && product.getNutritionLineItems() != null) {
            String baseUnit = (product.getNutritionBaseUnitValue() != null
                    ? product.getNutritionBaseUnitValue().stripTrailingZeros().toPlainString() : "100")
                    + defaultStr(product.getNutritionBaseUnitType(), "g");
            String headerTitle = "NUTRITIONAL INFORMATION (Approx. per " + sanitizeText(baseUnit) + ")";

            float tableTopY = cursorY;
            float headerH = 16f;
            float rowH = 14f;
            int rowsCount = product.getNutritionLineItems().size();
            float totalTableH = headerH + (rowsCount * rowH);

            cs.setLineWidth(1.0f);
            cs.addRect(0, tableTopY - totalTableH, virtualW, totalTableH);
            cs.stroke();

            drawCenteredText(cs, fontBold, 9f, headerTitle, 2f, virtualW - 4f, tableTopY - 12f);
            drawSolidLine(cs, 0, tableTopY - headerH, virtualW, tableTopY - headerH, 1.0f);

            float rowTop = tableTopY - headerH;
            float colSplitX = virtualW * 0.6f;
            for (NutritionLineItem item : product.getNutritionLineItems()) {
                float textBaseline = rowTop - 10f;
                drawLeftText(cs, fontRegular, 9f, sanitizeText(defaultStr(item.getComponentName(), "")), 4f, textBaseline);
                String valStr = (item.getAmount() != null ? item.getAmount().stripTrailingZeros().toPlainString() : "0")
                        + defaultStr(item.getMeasurementUnit(), "");
                drawRightText(cs, fontRegular, 9f, sanitizeText(valStr), virtualW - 4f, textBaseline);
                rowTop -= rowH;
                if (rowTop > tableTopY - totalTableH + 1f) {
                    drawSolidLine(cs, 0, rowTop, virtualW, rowTop, 0.75f);
                }
            }
            drawSolidLine(cs, colSplitX, tableTopY - headerH, colSplitX, tableTopY - totalTableH, 1.0f);
            cursorY = tableTopY - totalTableH - 4f;
        }

        // 5. Storage Instructions (Dashed Box)
        if (product != null && product.getStorageInstructions() != null && !product.getStorageInstructions().trim().isEmpty()) {
            List<String> storageLines = wrapText(fontRegular, 11f, sanitizeText(product.getStorageInstructions()), virtualW - 12f);
            float boxH = (storageLines.size() * 13f) + 8f;
            float boxBottom = cursorY - boxH;

            cs.setLineWidth(1.5f);
            cs.setLineDashPattern(new float[]{4f, 2f}, 0);
            cs.addRect(0, boxBottom, virtualW, boxH);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);

            float sY = cursorY - 12f;
            for (String line : storageLines) {
                drawCenteredText(cs, fontRegular, 11f, line, 4f, virtualW - 8f, sY);
                sY -= 13f;
            }
        }
    }

    private void drawSolidLine(PDPageContentStream cs, float x1, float y1, float x2, float y2, float width) throws IOException {
        cs.setLineWidth(width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void drawFilledCircle(PDPageContentStream cs, float cx, float cy, float r, Color color) throws IOException {
        final float k = 0.552284749831f;
        cs.setNonStrokingColor(color);
        cs.moveTo(cx - r, cy);
        cs.curveTo(cx - r, cy + k * r, cx - k * r, cy + r, cx, cy + r);
        cs.curveTo(cx + k * r, cy + r, cx + r, cy + k * r, cx + r, cy);
        cs.curveTo(cx + r, cy - k * r, cx + k * r, cy - r, cx, cy - r);
        cs.curveTo(cx - k * r, cy - r, cx - r, cy - k * r, cx - r, cy);
        cs.fill();
    }

    private void drawLeftText(PDPageContentStream cs, PDFont font, float fontSize, String text, float x, float y) throws IOException {
        if (text == null || text.isEmpty()) return;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawCenteredText(PDPageContentStream cs, PDFont font, float fontSize, String text, float boxX, float boxW, float y) throws IOException {
        if (text == null || text.isEmpty()) return;
        float textW = (font.getStringWidth(text) / 1000f) * fontSize;
        float startX = boxX + Math.max(0f, (boxW - textW) / 2f);
        drawLeftText(cs, font, fontSize, text, startX, y);
    }

    private void drawRightText(PDPageContentStream cs, PDFont font, float fontSize, String text, float rightX, float y) throws IOException {
        if (text == null || text.isEmpty()) return;
        float textW = (font.getStringWidth(text) / 1000f) * fontSize;
        drawLeftText(cs, font, fontSize, text, rightX - textW, y);
    }

    private List<String> wrapText(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\r?\\n");
        for (String para : paragraphs) {
            String[] words = para.trim().split("\\s+");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                float width = (font.getStringWidth(candidate) / 1000f) * fontSize;
                if (width <= maxWidth || currentLine.isEmpty()) {
                    currentLine.setLength(0);
                    currentLine.append(candidate);
                } else {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentLine.append(word);
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    private String sanitizeText(String input) {
        if (input == null) return "";
        // Replace characters outside WinAnsiEncoding (e.g., Rupee symbol ₹) with safe equivalents for Standard 14 fonts
        return input
                .replace("₹", "Rs.")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF\\r\\n]", "?");
    }

    private String defaultStr(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val : fallback;
    }
}
