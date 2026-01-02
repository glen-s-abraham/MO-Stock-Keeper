package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.SalesOrder;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import com.mushroom.stockkeeper.repository.SalesOrderRepository;
import com.mushroom.stockkeeper.repository.InventoryUnitRepository;
import com.mushroom.stockkeeper.service.SalesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.mushroom.stockkeeper.dto.OrderSummaryRow;
import com.mushroom.stockkeeper.model.InventoryUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/sales")
public class SalesController {

    private final com.mushroom.stockkeeper.repository.InvoiceRepository invoiceRepository;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SalesController.class);
    private final SalesService salesService;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryUnitRepository unitRepository;
    private final com.mushroom.stockkeeper.repository.HarvestBatchRepository batchRepository;

    public SalesController(SalesService salesService, SalesOrderRepository orderRepository,
            CustomerRepository customerRepository, InventoryUnitRepository unitRepository,
            com.mushroom.stockkeeper.repository.InvoiceRepository invoiceRepository,
            com.mushroom.stockkeeper.repository.HarvestBatchRepository batchRepository) {
        this.salesService = salesService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.batchRepository = batchRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", orderRepository.findAll());
        return "sales/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("customers", customerRepository.findByIsHiddenFalse());
        return "sales/create";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam String orderType,
            // Retail Params
            @RequestParam(required = false) String retailName,
            @RequestParam(required = false) String retailPhone,
            // Payment method moved to Finalization
            @RequestParam(defaultValue = "false") boolean saveRetailCustomer,
            // Wholesale Params
            @RequestParam(required = false) Long wholesaleCustomerId,
            @RequestParam(required = false) String wholesaleNewName,
            @RequestParam(required = false) String wholesaleNewPhone,
            @RequestParam(defaultValue = "existing") String wsMode,

            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        com.mushroom.stockkeeper.model.Customer customer = null;
        String paymentMethod = null;

        if (com.mushroom.stockkeeper.model.CustomerType.RETAIL.name().equalsIgnoreCase(orderType)) {
            // RETAIL FLOW
            String name = (retailName != null && !retailName.trim().isEmpty()) ? retailName.trim() : "Walk-in Guest";

            if (saveRetailCustomer) {
                // "Save to Contacts" -> Create Regular Customer
                customer = new com.mushroom.stockkeeper.model.Customer();
                customer.setName(name);
                customer.setPhoneNumber(retailPhone);
                customer.setHidden(false);
                customer.setType(com.mushroom.stockkeeper.model.CustomerType.RETAIL); // Explicitly RETAIL
                customerRepository.save(customer);
            } else {
                // "One-off" -> Walk-in/Guest
                customer = new com.mushroom.stockkeeper.model.Customer();
                customer.setName(name);
                customer.setPhoneNumber(retailPhone);
                customer.setHidden(true); // HIDDEN from dropdowns
                customer.setType(com.mushroom.stockkeeper.model.CustomerType.RETAIL); // Explicitly RETAIL
                customerRepository.save(customer);
            }
            // Payment captured at finalization
            paymentMethod = null;

        } else {
            // WHOLESALE FLOW
            if ("new".equalsIgnoreCase(wsMode)) {
                if (wholesaleNewName == null || wholesaleNewName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Account Name required for new Wholesale customer");
                }
                customer = new com.mushroom.stockkeeper.model.Customer();
                customer.setName(wholesaleNewName.trim());
                customer.setPhoneNumber(wholesaleNewPhone);
                customer.setHidden(false);
                customerRepository.save(customer);
            } else {
                if (wholesaleCustomerId == null) {
                    throw new IllegalArgumentException("Customer selection required");
                }
                customer = customerRepository.findById(wholesaleCustomerId).orElseThrow();
            }
            // Wholesale typically Credit (Unpaid initially), or could be mixed. Payment
            // Method null initially.
            paymentMethod = null;
        }

        SalesOrder so = salesService.createOrder(customer, orderType, paymentMethod);
        redirectAttributes.addFlashAttribute("promptPickingMode", true);
        return "redirect:/sales/" + so.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        SalesOrder so = orderRepository.findById(id).orElseThrow();
        model.addAttribute("order", so);

        // Try to fetch existing invoice to get official total
        java.util.Optional<com.mushroom.stockkeeper.model.Invoice> invoiceOpt = invoiceRepository.findBySalesOrder(so);

        // Group units by Product to build summary
        Map<Long, List<InventoryUnit>> unitsByProduct = so
                .getAllocatedUnits().stream()
                .collect(Collectors.groupingBy(u -> u.getBatch().getProduct().getId()));

        List<OrderSummaryRow> summaryRows = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (Map.Entry<Long, List<InventoryUnit>> entry : unitsByProduct
                .entrySet()) {
            List<InventoryUnit> units = entry.getValue();
            if (units.isEmpty())
                continue;

            com.mushroom.stockkeeper.model.Product product = units.get(0).getBatch().getProduct();
            long qty = units.size();
            // Assume homogeneous price for product in order for now, take first non-null or
            // zero
            BigDecimal unitPrice = units.stream()
                    .map(InventoryUnit::getSoldPrice)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            // If Invoiced, try to infer unit price from Invoice total if local unit price
            // is missing?
            // Better to rely on what was stored.

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(qty));

            summaryRows.add(new OrderSummaryRow(
                    product.getId(),
                    product.getName(),
                    qty,
                    unitPrice,
                    subtotal));
            calculatedTotal = calculatedTotal.add(subtotal);
        }

        BigDecimal grandTotal = calculatedTotal;
        if (invoiceOpt.isPresent()) {
            BigDecimal invTotal = invoiceOpt.get().getTotalAmount();
            if (invTotal != null && invTotal.compareTo(BigDecimal.ZERO) > 0) {
                grandTotal = invTotal;
                logger.info("Order {} is Invoiced. Using Invoice Total: {}", id, grandTotal);
            } else {
                logger.warn("Order {} is Invoiced but Total is 0/null. Falling back to Calculated Total: {}", id,
                        calculatedTotal);
                grandTotal = calculatedTotal;
            }
        } else {
            logger.info("Order {} is Draft. Calculated Total: {}", id, grandTotal);
        }

        model.addAttribute("summaryRows", summaryRows);
        model.addAttribute("grandTotal", grandTotal);

        // Add available inventory for manual selection
        model.addAttribute("availableUnits",
                unitRepository.findByStatus(com.mushroom.stockkeeper.model.InventoryStatus.AVAILABLE));

        // Add batches for filtering
        model.addAttribute("availableBatches", batchRepository.findAll(org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "batchDate")));

        return "sales/picking"; // The Scanning Interface
    }

    // Ajax Endpoints for Scanning
    @PostMapping("/{id}/allocate")
    @ResponseBody
    public ResponseEntity<?> allocate(@PathVariable Long id, @RequestBody String qrContent) {
        try {
            salesService.allocateUnit(id, qrContent);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/update-price")
    @ResponseBody
    public ResponseEntity<?> updatePrice(@PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        try {
            Long productId = Long.valueOf(payload.get("productId").toString());
            java.math.BigDecimal price = new java.math.BigDecimal(payload.get("price").toString());
            salesService.updateProductPrice(id, productId, price);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/update-discount")
    @ResponseBody
    public ResponseEntity<?> updateDiscount(@PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        try {
            java.math.BigDecimal discountPercentage = new java.math.BigDecimal(payload.get("discount").toString());
            salesService.updateOrderDiscount(id, discountPercentage);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/remove")
    public String removeUnit(@PathVariable Long id, @RequestParam Long unitId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            salesService.removeUnit(unitId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/sales/" + id;
    }

    @PostMapping("/{id}/finalize")
    public String finalizeOrder(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean isPaid,
            @RequestParam(required = false) String paymentMethod,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            salesService.finalizeOrder(id, isPaid, paymentMethod);
            redirectAttributes.addFlashAttribute("success", "Order finalized and invoice generated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/sales/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            salesService.cancelOrder(id);
            redirectAttributes.addFlashAttribute("success", "Order has been cancelled and stock reverted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to cancel order: " + e.getMessage());
        }
        return "redirect:/sales/" + id;
    }
}
