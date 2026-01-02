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
            Model model) {
        HarvestBatch batch = batchRepository.findById(id).orElseThrow();

        List<InventoryUnit> units;

        // Base List
        // Optimized: Use direct DB query
        List<InventoryUnit> allBatchUnits = unitRepository.findByBatchId(id);

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
