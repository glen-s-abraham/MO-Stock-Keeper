package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.ManualLabelRange;
import com.mushroom.stockkeeper.dto.PrintServerStatusResponse;
import com.mushroom.stockkeeper.dto.ServerPrintResponse;
import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import com.mushroom.stockkeeper.service.BatchService;
import com.mushroom.stockkeeper.service.PrintServerService;
import com.mushroom.stockkeeper.service.QrCodeService;
import com.mushroom.stockkeeper.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

@Controller
@RequestMapping("/batches")
public class HarvestBatchController {

    private final HarvestBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final InventoryUnitRepository unitRepository;
    private final BatchService batchService;
    private final QrCodeService qrCodeService;
    private final com.mushroom.stockkeeper.service.SettingsService settingsService;
    private final PrintServerService printServerService;

    public HarvestBatchController(HarvestBatchRepository batchRepository, ProductRepository productRepository,
            InventoryUnitRepository unitRepository, BatchService batchService, QrCodeService qrCodeService,
            com.mushroom.stockkeeper.service.SettingsService settingsService,
            PrintServerService printServerService) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.batchService = batchService;
        this.qrCodeService = qrCodeService;
        this.settingsService = settingsService;
        this.printServerService = printServerService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q, // Search query
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "all") String filter, // Keeping legacy filter param for compatibility or
                                                               // explicit status
                                                               // filtering
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "batchDate"));

        Specification<HarvestBatch> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search by Batch Code
            if (q != null && !q.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("batchCode")), "%" + q.toLowerCase() + "%"));
            }

            // Filter by Product
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }

            // Date Range (Batch Date)
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("batchDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("batchDate"), endDate));
            }

            // Status Filter using Legacy Param
            LocalDate today = LocalDate.now();
            if ("active".equalsIgnoreCase(filter)) {
                // Expiry >= Today OR Null
                Predicate notExpired = cb.or(
                        cb.greaterThanOrEqualTo(root.get("expiryDate"), today),
                        cb.isNull(root.get("expiryDate")));
                predicates.add(notExpired);
            } else if ("expired".equalsIgnoreCase(filter)) {
                predicates.add(cb.lessThan(root.get("expiryDate"), today));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<HarvestBatch> batchesPage = batchRepository.findAll(spec, pageable);

        // Fetch Stats (Global - unrelated to search for dashboard feel, or filtered?
        // Let's
        // keep global for now as headers)
        LocalDate now = LocalDate.now();
        // Optimize: these counts could be cached
        Map<Long, Long> activeCounts = batchRepository.countActiveBatchesGrouped(now).stream()
                .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));
        Map<Long, Long> expiredCounts = batchRepository.countExpiredBatchesGrouped(now).stream()
                .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));

        model.addAttribute("batchesPage", batchesPage); // Use Page object
        model.addAttribute("batches", batchesPage.getContent()); // Maintain compatibility
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("currentFilter", filter);
        model.addAttribute("currentProductId", productId);
        model.addAttribute("activeCounts", activeCounts);
        model.addAttribute("expiredCounts", expiredCounts);

        return "batches/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("products", productRepository.findAll());
        return "batches/create";
    }

    @GetMapping("/manual-labels")
    public String manualLabelsForm(Model model) {
        int lastSequence = settingsService.getManualLabelLastSequence();
        int nextSequence = lastSequence + 1;
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("lastSequence", lastSequence);
        model.addAttribute("nextSequence", nextSequence);
        model.addAttribute("nextBatchCode", ManualLabelRange.formatCode(nextSequence));
        return "batches/manual-labels";
    }

    @PostMapping("/manual-labels/generate")
    public String generateManualLabels(
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            if (productId == null || productRepository.findById(productId).isEmpty()) {
                throw new IllegalArgumentException("Please select a valid product.");
            }
            if (quantity == null || quantity < 1 || quantity > SettingsService.MAX_MANUAL_LABELS_PER_RUN) {
                throw new IllegalArgumentException(
                        "Quantity must be between 1 and " + SettingsService.MAX_MANUAL_LABELS_PER_RUN + " stickers.");
            }
            ManualLabelRange range = settingsService.allocateManualLabelSequence(quantity);
            return "redirect:/batches/manual-labels/print?productId=" + productId
                    + "&seqStart=" + range.getStartSeq()
                    + "&seqEnd=" + range.getEndSeq();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/batches/manual-labels";
        }
    }

    @GetMapping("/manual-labels/print")
    public String printManualLabels(
            @RequestParam Long productId,
            @RequestParam Integer seqStart,
            @RequestParam Integer seqEnd,
            jakarta.servlet.http.HttpServletRequest request,
            Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("error", "Product not found.");
            return "redirect:/batches/manual-labels";
        }
        if (seqStart == null || seqEnd == null || seqStart < 1 || seqEnd < seqStart
                || (seqEnd - seqStart + 1) > SettingsService.MAX_MANUAL_LABELS_PER_RUN) {
            redirectAttributes.addFlashAttribute("error",
                    "Invalid manual label sequence range. Please specify between 1 and "
                            + SettingsService.MAX_MANUAL_LABELS_PER_RUN + " stickers.");
            return "redirect:/batches/manual-labels";
        }

        HarvestBatch transientBatch = buildManualTransientBatch(product, seqStart, seqEnd);
        List<InventoryUnit> transientUnits = buildManualTransientUnits(transientBatch, seqStart, seqEnd);

        Map<Long, String> qrCodes = transientUnits.stream().collect(Collectors.toMap(
                InventoryUnit::getId,
                u -> ""));

        String clientIp = extractClientIp(request);
        boolean isSameMachine = printServerService.isClientSameAsPrintServer(clientIp);
        boolean hideServerPrintOnWorkstation = settingsService.isHideServerPrintOnWorkstation();
        boolean hideClientPrintOnRemote = settingsService.isHideClientPrintOnRemote();

        PrintServerStatusResponse status = (isSameMachine && hideServerPrintOnWorkstation)
                ? new PrintServerStatusResponse(false, settingsService.getPrintServerUrl(),
                        "Client is on the print server workstation (use direct Print)", true)
                : printServerService.checkConnectivity();
        boolean printServerAvailable = !(isSameMachine && hideServerPrintOnWorkstation)
                && status != null && status.isAvailable();
        boolean hideServerPrint = isSameMachine && hideServerPrintOnWorkstation;
        boolean hideClientPrint = !isSameMachine && hideClientPrintOnRemote;

        model.addAttribute("batch", transientBatch);
        model.addAttribute("units", transientUnits);
        model.addAttribute("qrCodes", qrCodes);
        model.addAttribute("manualMode", true);
        model.addAttribute("backUrl", "/batches/manual-labels");
        model.addAttribute("serverPrintUrl", "/batches/manual-labels/server-print");
        model.addAttribute("companyName", settingsService.getCompanyName());
        model.addAttribute("contactNumber", settingsService.getContactNumber());
        model.addAttribute("registeredOfficeAddress", settingsService.getRegisteredOfficeAddress());
        model.addAttribute("customerCareAddress", settingsService.getCustomerCareAddress());
        model.addAttribute("labelSheetSize", settingsService.getLabelSheetSize());
        model.addAttribute("customLabelWidth", settingsService.getCustomLabelWidth());
        model.addAttribute("customLabelHeight", settingsService.getCustomLabelHeight());
        model.addAttribute("printServerAvailable", printServerAvailable);
        model.addAttribute("sameMachineAsPrintServer", isSameMachine);
        model.addAttribute("hideServerPrintOnWorkstation", hideServerPrintOnWorkstation);
        model.addAttribute("hideClientPrintOnRemote", hideClientPrintOnRemote);
        model.addAttribute("hideServerPrint", hideServerPrint);
        model.addAttribute("hideClientPrint", hideClientPrint);
        model.addAttribute("printServerUrl", settingsService.getPrintServerUrl());
        model.addAttribute("printServerMessage", status != null ? status.getMessage() : "Print server unavailable");
        model.addAttribute("seqStart", seqStart);
        model.addAttribute("seqEnd", seqEnd);
        return "batches/print";
    }

    @PostMapping("/manual-labels/server-print")
    @ResponseBody
    public ResponseEntity<ServerPrintResponse> serverPrintManualLabels(
            @RequestParam Long productId,
            @RequestParam Integer seqStart,
            @RequestParam Integer seqEnd) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ServerPrintResponse(false, 0L, 0, 0, "Product not found"));
        }
        if (seqStart == null || seqEnd == null || seqStart < 1 || seqEnd < seqStart
                || (seqEnd - seqStart + 1) > SettingsService.MAX_MANUAL_LABELS_PER_RUN) {
            return ResponseEntity.badRequest()
                    .body(new ServerPrintResponse(false, 0L, 0, 0,
                            "Invalid manual label sequence range"));
        }

        HarvestBatch transientBatch = buildManualTransientBatch(product, seqStart, seqEnd);
        List<InventoryUnit> transientUnits = buildManualTransientUnits(transientBatch, seqStart, seqEnd);

        ServerPrintResponse response = printServerService.printBatchLabels(transientBatch, transientUnits);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    @PostMapping("/manual-labels/sequence")
    public String updateManualLabelSequence(
            @RequestParam Integer lastSequence,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            if (lastSequence == null || lastSequence < 0) {
                throw new IllegalArgumentException("Sequence number must be 0 or greater.");
            }
            settingsService.setManualLabelLastSequence(lastSequence);
            redirectAttributes.addFlashAttribute("success",
                    "Manual label sequence updated. Next sticker set will start from "
                            + ManualLabelRange.formatCode(lastSequence + 1) + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/batches/manual-labels";
    }

    private HarvestBatch buildManualTransientBatch(Product product, int seqStart, int seqEnd) {
        HarvestBatch batch = new HarvestBatch();
        batch.setId(0L);
        batch.setProduct(product);
        batch.setTotalUnits(seqEnd - seqStart + 1);
        batch.setBatchDate(null);
        batch.setExpiryDate(null);
        String rangeLabel = seqStart == seqEnd
                ? ManualLabelRange.formatCode(seqStart)
                : ManualLabelRange.formatCode(seqStart) + " to " + ManualLabelRange.formatCode(seqEnd);
        batch.setBatchCode(rangeLabel);
        return batch;
    }

    private List<InventoryUnit> buildManualTransientUnits(HarvestBatch batch, int seqStart, int seqEnd) {
        List<InventoryUnit> units = new ArrayList<>(seqEnd - seqStart + 1);
        for (int seq = seqStart; seq <= seqEnd; seq++) {
            InventoryUnit unit = new InventoryUnit();
            unit.setId((long) seq);
            unit.setBatch(batch);
            String code = ManualLabelRange.formatCode(seq);
            unit.setUuid(code);
            unit.setQrCodeContent("U:" + code);
            units.add(unit);
        }
        return units;
    }

    @PostMapping("/save")
    public String save(@RequestParam Long productId, @RequestParam Integer quantity,
            @RequestParam(required = false) LocalDate batchDate) {
        Product product = productRepository.findById(productId).orElseThrow();
        HarvestBatch savedBatch = batchService.createBatch(product, quantity, batchDate);
        return "redirect:/batches/" + savedBatch.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        HarvestBatch batch = batchRepository.findById(id).orElseThrow();
        // Since we don't have a direct List<Unit> in Batch entity (to save memory/lazy
        // load), we fetch from repo
        // Wait, I should have added @OneToMany in HarvestBatch for convenience or use
        // Repository.
        // I didn't add it in the Entity step (to keep it simple). Let's use Repository.
        // Actually, for "Print", we need them.
        // Since we don't have a direct List<Unit> in Batch entity (to save memory/lazy
        // load), we fetch from repo
        // Optimized: Use direct DB query
        List<InventoryUnit> units = unitRepository.findByBatchId(id);

        model.addAttribute("batch", batch);
        model.addAttribute("units", units);
        return "batches/detail";
    }

    @GetMapping("/{id}/print")
    public String printLabels(@PathVariable Long id,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Integer seqStart,
            @RequestParam(required = false) Integer seqEnd,
            jakarta.servlet.http.HttpServletRequest request,
            Model model) {
        HarvestBatch batch = batchRepository.findById(id).orElseThrow();

        List<InventoryUnit> units = filterBatchUnits(id, unitId, seqStart, seqEnd);

        // Generate Base64 QRs for each unit
        // Map<UnitId, Base64String>
        Map<Long, String> qrCodes = units.stream().collect(Collectors.toMap(
                InventoryUnit::getId,
                u -> qrCodeService.generateQrCodeBase64(u.getQrCodeContent(), 150, 150)));

        String clientIp = extractClientIp(request);
        boolean isSameMachine = printServerService.isClientSameAsPrintServer(clientIp);
        boolean hideServerPrintOnWorkstation = settingsService.isHideServerPrintOnWorkstation();
        boolean hideClientPrintOnRemote = settingsService.isHideClientPrintOnRemote();

        PrintServerStatusResponse status = (isSameMachine && hideServerPrintOnWorkstation)
                ? new PrintServerStatusResponse(false, settingsService.getPrintServerUrl(),
                        "Client is on the print server workstation (use direct Print)", true)
                : printServerService.checkConnectivity();
        boolean printServerAvailable = !(isSameMachine && hideServerPrintOnWorkstation) && status != null && status.isAvailable();
        boolean hideServerPrint = isSameMachine && hideServerPrintOnWorkstation;
        boolean hideClientPrint = !isSameMachine && hideClientPrintOnRemote;

        model.addAttribute("batch", batch);
        model.addAttribute("units", units);
        model.addAttribute("qrCodes", qrCodes);
        model.addAttribute("companyName", settingsService.getCompanyName());
        model.addAttribute("contactNumber", settingsService.getContactNumber());
        model.addAttribute("registeredOfficeAddress", settingsService.getRegisteredOfficeAddress());
        model.addAttribute("customerCareAddress", settingsService.getCustomerCareAddress());
        model.addAttribute("labelSheetSize", settingsService.getLabelSheetSize());
        model.addAttribute("customLabelWidth", settingsService.getCustomLabelWidth());
        model.addAttribute("customLabelHeight", settingsService.getCustomLabelHeight());
        model.addAttribute("printServerAvailable", printServerAvailable);
        model.addAttribute("sameMachineAsPrintServer", isSameMachine);
        model.addAttribute("hideServerPrintOnWorkstation", hideServerPrintOnWorkstation);
        model.addAttribute("hideClientPrintOnRemote", hideClientPrintOnRemote);
        model.addAttribute("hideServerPrint", hideServerPrint);
        model.addAttribute("hideClientPrint", hideClientPrint);
        model.addAttribute("printServerUrl", settingsService.getPrintServerUrl());
        model.addAttribute("printServerMessage", status != null ? status.getMessage() : "Print server unavailable");
        model.addAttribute("unitId", unitId);
        model.addAttribute("seqStart", seqStart);
        model.addAttribute("seqEnd", seqEnd);
        return "batches/print";
    }

    @GetMapping("/print-server/status")
    @ResponseBody
    public ResponseEntity<PrintServerStatusResponse> printServerStatus(jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        boolean isSameMachine = printServerService.isClientSameAsPrintServer(clientIp);
        boolean hideServerPrintOnWorkstation = settingsService.isHideServerPrintOnWorkstation();
        boolean hideClientPrintOnRemote = settingsService.isHideClientPrintOnRemote();

        if (isSameMachine && hideServerPrintOnWorkstation) {
            PrintServerStatusResponse workstationStatus = new PrintServerStatusResponse(
                    false,
                    settingsService.getPrintServerUrl(),
                    "Client is on the print server workstation (use direct Print)",
                    true);
            workstationStatus.setHideServerPrintOnWorkstation(hideServerPrintOnWorkstation);
            workstationStatus.setHideClientPrintOnRemote(hideClientPrintOnRemote);
            return ResponseEntity.ok(workstationStatus);
        }
        PrintServerStatusResponse status = printServerService.checkConnectivity();
        if (status != null) {
            status.setSameMachineAsPrintServer(isSameMachine);
            status.setHideServerPrintOnWorkstation(hideServerPrintOnWorkstation);
            status.setHideClientPrintOnRemote(hideClientPrintOnRemote);
        }
        return ResponseEntity.ok(status);
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // Only use CF-Connecting-IP / X-Forwarded-For if present, otherwise remoteAddr
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.trim().isEmpty()) {
            return cfIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/{id}/server-print")
    @ResponseBody
    public ResponseEntity<ServerPrintResponse> serverPrint(@PathVariable Long id,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Integer seqStart,
            @RequestParam(required = false) Integer seqEnd) {
        HarvestBatch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ServerPrintResponse(false, id, 0, 0, "Batch not found"));
        }

        List<InventoryUnit> units = filterBatchUnits(id, unitId, seqStart, seqEnd);
        if (units.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ServerPrintResponse(false, id, 0, 0, "No inventory units match the selected print filter"));
        }

        ServerPrintResponse response = printServerService.printBatchLabels(batch, units);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    private List<InventoryUnit> filterBatchUnits(Long batchId, Long unitId, Integer seqStart, Integer seqEnd) {
        List<InventoryUnit> allBatchUnits = unitRepository.findByBatchId(batchId);
        if (unitId != null) {
            return allBatchUnits.stream()
                    .filter(u -> u.getId().equals(unitId))
                    .collect(Collectors.toList());
        } else if (seqStart != null && seqEnd != null) {
            return allBatchUnits.stream()
                    .filter(u -> {
                        try {
                            String uuid = u.getUuid();
                            String seqStr = uuid.substring(uuid.lastIndexOf('-') + 1);
                            int seq = Integer.parseInt(seqStr);
                            return seq >= seqStart && seq <= seqEnd;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            return allBatchUnits;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            batchService.deleteBatch(id);
            redirectAttributes.addFlashAttribute("success", "Batch deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/batches/" + id;
        }
        return "redirect:/batches";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @RequestParam LocalDate batchDate,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            batchService.updateBatch(id, batchDate);
            redirectAttributes.addFlashAttribute("success", "Batch updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/batches/" + id;
    }

    @PostMapping("/units/{unitId}/spoil")
    public String markSpoiled(@PathVariable Long unitId, @RequestParam Long batchId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            batchService.markUnitSpoiled(unitId);
            redirectAttributes.addFlashAttribute("success", "Unit marked as spoiled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/batches/" + batchId;
    }
}
