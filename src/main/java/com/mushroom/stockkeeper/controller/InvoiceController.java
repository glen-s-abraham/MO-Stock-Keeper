package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.CustomerInvoiceSummary;
import com.mushroom.stockkeeper.model.Customer;
import com.mushroom.stockkeeper.model.Invoice;
import com.mushroom.stockkeeper.model.InvoiceStatus;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import com.mushroom.stockkeeper.repository.InvoiceRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository;

    public InvoiceController(InvoiceRepository invoiceRepository, CustomerRepository customerRepository,
            com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditNoteRepository = creditNoteRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Customer> customers = customerRepository.findAll();
        List<CustomerInvoiceSummary> summaries = new ArrayList<>();

        // Optimizing: Fetch all invoices and group by customer.
        List<Invoice> allInvoices = invoiceRepository.findAll();
        Map<Customer, List<Invoice>> invoicesByCustomer = allInvoices.stream()
                .collect(Collectors.groupingBy(Invoice::getCustomer));

        List<com.mushroom.stockkeeper.model.CreditNote> allCredits = creditNoteRepository.findAll();
        Map<Customer, List<com.mushroom.stockkeeper.model.CreditNote>> creditsByCustomer = allCredits.stream()
                .collect(Collectors.groupingBy(com.mushroom.stockkeeper.model.CreditNote::getCustomer));

        for (Customer customer : customers) {
            List<Invoice> custInvoices = invoicesByCustomer.getOrDefault(customer, new ArrayList<>());
            List<com.mushroom.stockkeeper.model.CreditNote> custCredits = creditsByCustomer.getOrDefault(customer,
                    new ArrayList<>());

            BigDecimal totalBalance = BigDecimal.ZERO;
            long unpaid = 0;
            long partial = 0;
            long paid = 0;

            for (Invoice inv : custInvoices) {
                totalBalance = totalBalance.add(inv.getBalanceDue() != null ? inv.getBalanceDue() : BigDecimal.ZERO);
                if (inv.getStatus() == InvoiceStatus.UNPAID)
                    unpaid++;
                else if (inv.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                    partial++;
                else if (inv.getStatus() == InvoiceStatus.PAID)
                    paid++;
            }

            BigDecimal totalCredits = custCredits.stream()
                    .map(com.mushroom.stockkeeper.model.CreditNote::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Only add if there is some activity? Or always?
            if (!custInvoices.isEmpty() || !custCredits.isEmpty()) {
                summaries.add(new CustomerInvoiceSummary(
                        customer.getId(),
                        customer.getName(),
                        totalBalance,
                        unpaid,
                        partial,
                        paid,
                        totalCredits));
            }
        }

        model.addAttribute("summaries", summaries);
        return "invoices/dashboard";
    }

    @GetMapping("/customer/{customerId}")
    public String listByCustomer(@PathVariable Long customerId, Model model) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer Id:" + customerId));

        List<Invoice> invoices = invoiceRepository.findByCustomerIdOrderByInvoiceDateDesc(customerId)
                .orElse(new ArrayList<>());

        // Fetch Credit Notes using stream filter for now
        List<com.mushroom.stockkeeper.model.CreditNote> creditNotes = creditNoteRepository.findAll().stream()
                .filter(c -> c.getCustomer().getId().equals(customerId))
                .collect(Collectors.toList());

        model.addAttribute("customer", customer);
        model.addAttribute("invoices", invoices);
        model.addAttribute("creditNotes", creditNotes);

        return "invoices/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invoice Id:" + id));
        model.addAttribute("invoice", invoice);
        model.addAttribute("creditNotes", invoice.getCreditNotes()); // Assuming lazy load works or transaction is open
        return "invoices/detail";
    }
}
