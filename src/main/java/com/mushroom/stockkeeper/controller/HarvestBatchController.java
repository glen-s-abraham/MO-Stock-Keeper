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

    public HarvestBatchController(HarvestBatchRepository batchRepository, ProductRepository productRepository,
            InventoryUnitRepository unitRepository, BatchService batchService, QrCodeService qrCodeService) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.batchService = batchService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("batches", batchRepository.findAll());
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
        batchService.createBatch(product, quantity, batchDate);
        return "redirect:/batches";
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
    public String printLabels(@PathVariable Long id, Model model) {
        HarvestBatch batch = batchRepository.findById(id).orElseThrow();

        // Optimize: Implement findByBatchId in repo in next refactor
        List<InventoryUnit> units = unitRepository.findAll().stream()
                .filter(u -> u.getBatch().getId().equals(id))
                .collect(Collectors.toList());

        // Generate Base64 QRs for each unit
        // Map<UnitId, Base64String>
        Map<Long, String> qrCodes = units.stream().collect(Collectors.toMap(
                InventoryUnit::getId,
                u -> qrCodeService.generateQrCodeBase64(u.getQrCodeContent(), 150, 150)));

        model.addAttribute("batch", batch);
        model.addAttribute("units", units);
        model.addAttribute("qrCodes", qrCodes);
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
}
