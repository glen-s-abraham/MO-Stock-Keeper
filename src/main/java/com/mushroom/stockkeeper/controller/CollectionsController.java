package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.Customer;
import com.mushroom.stockkeeper.model.Invoice;
import com.mushroom.stockkeeper.model.InvoiceStatus;
import com.mushroom.stockkeeper.model.PaymentMethod;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import com.mushroom.stockkeeper.repository.InvoiceRepository;
import com.mushroom.stockkeeper.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/collections")
public class CollectionsController {

    private final PaymentService paymentService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    public CollectionsController(PaymentService paymentService, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository) {
        this.paymentService = paymentService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public String index(Model model) {
        // Aging Report Logic: Map<Customer, TotalDue>
        // Ideally should be a DTO/Query
        List<Customer> customers = customerRepository.findAll();
        Map<Long, BigDecimal> balances = new HashMap<>();

        for (Customer c : customers) {
            List<Invoice> unpaid = invoiceRepository.findByCustomerIdAndStatusNot(c.getId(), InvoiceStatus.PAID);
            BigDecimal totalDue = unpaid.stream()
                    .map(Invoice::getBalanceDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            balances.put(c.getId(), totalDue);
        }

        model.addAttribute("customers", customers);
        model.addAttribute("balances", balances);
        return "collections/index";
    }

    @GetMapping("/payment")
    public String paymentForm(Model model) {
        List<Customer> customers = customerRepository.findAll();
        Map<Long, BigDecimal> balances = new HashMap<>();

        for (Customer c : customers) {
            List<Invoice> unpaid = invoiceRepository.findByCustomerIdAndStatusNot(c.getId(), InvoiceStatus.PAID);
            BigDecimal totalDue = unpaid.stream()
                    .map(Invoice::getBalanceDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            balances.put(c.getId(), totalDue);
        }

        model.addAttribute("customers", customers);
        model.addAttribute("balances", balances);
        model.addAttribute("methods", PaymentMethod.values());
        return "collections/payment";
    }

    @PostMapping("/payment")
    public String recordPayment(@RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam PaymentMethod method,
            @RequestParam String reference,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        // Validate Amount
        List<Invoice> unpaid = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        BigDecimal totalDue = unpaid.stream()
                .map(Invoice::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.compareTo(totalDue) > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Payment amount (" + amount + ") exceeds total due (" + totalDue + ").");
            return "redirect:/collections/payment?customerId=" + customerId;
        }

        paymentService.recordPayment(customerId, amount, method, reference);
        redirectAttributes.addFlashAttribute("success", "Payment recorded successfully.");
        return "redirect:/collections";
    }
}
