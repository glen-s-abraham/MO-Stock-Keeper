package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.HarvestBatch;
import com.mushroom.stockkeeper.model.InventoryUnit;
import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.HarvestBatchRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.repository.ProductRepository;
import com.mushroom.stockkeeper.service.BatchService;
import com.mushroom.stockkeeper.service.QrCodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/batches")
public class HarvestBatchController {

    private final HarvestBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final InventoryUnitRepository unitRepository;
    private final BatchService batchService;
    private final QrCodeService qrCodeService;
    private final com.mushroom.stockkeeper.service.SettingsService settingsService;

    public HarvestBatchController(HarvestBatchRepository batchRepository, ProductRepository productRepository,
            InventoryUnitRepository unitRepository, BatchService batchService, QrCodeService qrCodeService,
            com.mushroom.stockkeeper.service.SettingsService settingsService) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.batchService = batchService;
        this.qrCodeService = qrCodeService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "active") String filter,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "false") boolean showAll,
            Model model) {

        // Mode 1: Product Selection (Default Landing)
        if (productId == null && !showAll) {
            model.addAttribute("products", productRepository.findAll());

            // Fetch Stats
            LocalDate now = LocalDate.now();
            Map<Long, Long> activeCounts = batchRepository.countActiveBatchesGrouped(now).stream()
                    .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));

            Map<Long, Long> expiredCounts = batchRepository.countExpiredBatchesGrouped(now).stream()
                    .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));

            model.addAttribute("activeCounts", activeCounts);
            model.addAttribute("expiredCounts", expiredCounts);

            return "batches/products";
        }

        // Mode 2: Batch List (Filtered)
        LocalDate today = LocalDate.now();
        List<HarvestBatch> batches;

        if (productId != null) {
            // Filter by Product + Status
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid product"));
            model.addAttribute("selectedProduct", product);

            switch (filter) {
                case "expired":
                    batches = batchRepository.findExpiredByProduct(productId, today);
                    break;
                case "all":
                    batches = batchRepository.findByProductId(productId);
                    break;
                case "active":
                default:
                    batches = batchRepository.findActiveByProduct(productId, today);
                    break;
            }
        } else {
            // Show All (Mixed) + Status
            switch (filter) {
                case "expired":
                    batches = batchRepository.findByExpiryDateLessThan(today);
                    break;
                case "all":
                    batches = batchRepository.findAll();
                    break;
                case "active":
                default:
                    batches = batchRepository.findByExpiryDateGreaterThanEqualOrExpiryDateIsNull(today);
                    break;
            }
        }

        // Sort DESC
        batches.sort(java.util.Comparator.comparing(HarvestBatch::getBatchDate).reversed());

        model.addAttribute("batches", batches);
        model.addAttribute("currentFilter", filter);
        model.addAttribute("currentProductId", productId);
        return "batches/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("products", productRepository.findAll());
        return "batches/create";
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
        List<InventoryUnit> units = unitRepository.findAll().stream() // Inefficient for production, should add method
                                                                      // in repo
                .filter(u -> u.getBatch().getId().equals(id))
                .collect(Collectors.toList());

        model.addAttribute("batch", batch);
        model.addAttribute("units", units);
        return "batches/detail";
    }

    @GetMapping("/{id}/print")
    public String printLabels(@PathVariable Long id,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Integer seqStart,
            @RequestParam(required = false) Integer seqEnd,
            Model model) {
        HarvestBatch batch = batchRepository.findById(id).orElseThrow();

        List<InventoryUnit> units;

        // Base List
        // Optimize: Implement findByBatchId in repo to avoid loading all
        List<InventoryUnit> allBatchUnits = unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(id))
                .collect(Collectors.toList());

        if (unitId != null) {
            // Print specific unit
            units = allBatchUnits.stream()
                    .filter(u -> u.getId().equals(unitId))
                    .collect(Collectors.toList());
        } else if (seqStart != null && seqEnd != null) {
            // Print Range (e.g. 1 to 50)
            // Assumption: UUID is BatchCode-SEQ.
            units = allBatchUnits.stream()
                    .filter(u -> {
                        try {
                            String uuid = u.getUuid();
                            String seqStr = uuid.substring(uuid.lastIndexOf('-') + 1);
                            int seq = Integer.parseInt(seqStr);
                            return seq >= seqStart && seq <= seqEnd;
                        } catch (Exception e) {
                            return false; // Skip malformed
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            // Print All
            units = allBatchUnits;
        }

        // Generate Base64 QRs for each unit
        // Map<UnitId, Base64String>
        Map<Long, String> qrCodes = units.stream().collect(Collectors.toMap(
                InventoryUnit::getId,
                u -> qrCodeService.generateQrCodeBase64(u.getQrCodeContent(), 150, 150)));

        model.addAttribute("batch", batch);
        model.addAttribute("units", units);
        model.addAttribute("qrCodes", qrCodes);
        model.addAttribute("companyName", settingsService.getCompanyName());
        model.addAttribute("contactNumber", settingsService.getContactNumber());
        model.addAttribute("labelSheetSize", settingsService.getLabelSheetSize());
        model.addAttribute("customLabelWidth", settingsService.getCustomLabelWidth());
        model.addAttribute("customLabelHeight", settingsService.getCustomLabelHeight());
        return "batches/print";
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
