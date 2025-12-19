package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;
import java.math.BigDecimal;
import com.mushroom.stockkeeper.dto.OrderSummaryRow;
import java.util.ArrayList;

@Controller
@RequestMapping("/sales")
public class InvoicePrintController {

    private final SalesOrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppSettingRepository settingRepository;

    public InvoicePrintController(SalesOrderRepository orderRepository, InvoiceRepository invoiceRepository,
            AppSettingRepository settingRepository) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.settingRepository = settingRepository;
    }

    @GetMapping("/{id}/print")
    public String printInvoice(@PathVariable Long id, Model model) {
        SalesOrder so = orderRepository.findById(id).orElseThrow();
        Invoice invoice = invoiceRepository.findBySalesOrder(so).orElse(null);

        // Fetch Settings
        Map<String, String> settings = settingRepository.findAll().stream()
                .collect(Collectors.toMap(AppSetting::getSettingKey, AppSetting::getSettingValue));
        model.addAttribute("settings", settings);

        model.addAttribute("order", so);
        model.addAttribute("invoice", invoice);

        // Calculate Summary Rows (Same logic as picking view, better to refactor but
        // duplicating for speed now)
        // Group units by Product
        Map<Long, List<InventoryUnit>> unitsByProduct = so.getAllocatedUnits().stream()
                .collect(Collectors.groupingBy(u -> u.getBatch().getProduct().getId()));

        List<OrderSummaryRow> summaryRows = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (Map.Entry<Long, List<InventoryUnit>> entry : unitsByProduct.entrySet()) {
            List<InventoryUnit> units = entry.getValue();
            if (units.isEmpty())
                continue;

            Product product = units.get(0).getBatch().getProduct();
            long qty = units.size();
            BigDecimal unitPrice = units.stream()
                    .map(InventoryUnit::getSoldPrice)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(qty));

            summaryRows.add(new OrderSummaryRow(product.getId(), product.getName(), qty, unitPrice, subtotal));
            calculatedTotal = calculatedTotal.add(subtotal);
        }

        model.addAttribute("summaryRows", summaryRows);

        // Use invoice total if available
        BigDecimal grandTotal = calculatedTotal;
        if (invoice != null && invoice.getTotalAmount() != null) {
            grandTotal = invoice.getTotalAmount();
        }
        model.addAttribute("grandTotal", grandTotal);

        return "sales/print_invoice";
    }
}
