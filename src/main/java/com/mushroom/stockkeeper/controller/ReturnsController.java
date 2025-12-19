package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.CreditNote;
import com.mushroom.stockkeeper.service.ReturnsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.model.InventoryStatus;
import com.mushroom.stockkeeper.dto.BatchReturnRequest;

@Controller
@RequestMapping("/returns")
public class ReturnsController {

    private final ReturnsService returnsService;
    private final com.mushroom.stockkeeper.repository.InventoryUnitRepository unitRepository;

    public ReturnsController(ReturnsService returnsService,
            com.mushroom.stockkeeper.repository.InventoryUnitRepository unitRepository) {
        this.returnsService = returnsService;
        this.unitRepository = unitRepository;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("soldUnits", unitRepository.findByStatus(InventoryStatus.SOLD));
        return "returns/scan";
    }

    @PostMapping("/validate")
    @ResponseBody
    public java.util.Map<String, Object> validateItem(@RequestBody java.util.Map<String, String> payload) {
        String uuid = payload.get("uuid");
        if (uuid == null)
            return java.util.Map.of("valid", false, "message", "No UUID provided");

        if (uuid.startsWith("U:"))
            uuid = uuid.substring(2);

        try {
            var unitOpt = unitRepository.findByUuid(uuid);
            if (unitOpt.isEmpty()) {
                return java.util.Map.of("valid", false, "message", "UUID not found");
            }
            var unit = unitOpt.get();
            if (unit.getStatus() != com.mushroom.stockkeeper.model.InventoryStatus.SOLD) {
                return java.util.Map.of("valid", false, "message", "Unit is " + unit.getStatus() + " (Must be SOLD)");
            }

            return java.util.Map.of(
                    "valid", true,
                    "message", "Valid",
                    "product", unit.getBatch().getProduct().getName(),
                    "sku", unit.getBatch().getProduct().getSku());
        } catch (Exception e) {
            return java.util.Map.of("valid", false, "message", "Error: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    @ResponseBody
    public java.util.Map<String, Object> batchProcess(
            @RequestBody com.mushroom.stockkeeper.dto.BatchReturnRequest request) {
        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (var item : request.getItems()) {
            try {
                returnsService.processReturn(item.getUuid(), item.getReason());
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.append(item.getUuid()).append(": ").append(e.getMessage()).append("; ");
            }
        }

        return java.util.Map.of(
                "success", failCount == 0,
                "processed", successCount,
                "failed", failCount,
                "errors", errors.toString(),
                "redirect", "/returns");
    }
}
