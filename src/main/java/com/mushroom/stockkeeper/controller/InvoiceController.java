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
                // Wholesale Summaries
                List<Customer> customers = customerRepository
                                .findByTypeAndIsHiddenFalse(com.mushroom.stockkeeper.model.CustomerType.WHOLESALE);

                // Fetch aggregated data efficiently
                List<Object[]> balancesData = invoiceRepository.findWholesaleOutstandingBalances(); // [customerId,
                                                                                                    // totalDue,
                                                                                                    // unpaidCount]
                List<Object[]> countsData = invoiceRepository.findWholesaleInvoiceCounts(); // [customerId, status,
                                                                                            // count]
                List<Object[]> creditsData = creditNoteRepository.findWholesaleRemainingCredits(); // [customerId,
                                                                                                   // totalCredit]

                // Map aggregated data
                Map<Long, BigDecimal> balanceMap = balancesData.stream().collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (BigDecimal) row[1]));

                Map<Long, BigDecimal> creditMap = creditsData.stream().collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (BigDecimal) row[1]));

                List<CustomerInvoiceSummary> summaries = new ArrayList<>();

                for (Customer c : customers) {
                        BigDecimal totalBalance = balanceMap.getOrDefault(c.getId(), BigDecimal.ZERO);
                        BigDecimal totalCredit = creditMap.getOrDefault(c.getId(), BigDecimal.ZERO);

                        // Count stats from countsData (Client-side filtering of list or pre-mapped?
                        // List is small, filtering ok)
                        // Or easier: Just use what we have. dashboard needs: Unpaid/Partial/Paid
                        // counts.
                        long unpaid = 0;
                        long partial = 0;
                        long paid = 0;

                        for (Object[] row : countsData) {
                                if (row[0].equals(c.getId())) {
                                        InvoiceStatus st = (InvoiceStatus) row[1];
                                        long count = (Long) row[2];
                                        if (st == InvoiceStatus.UNPAID)
                                                unpaid += count;
                                        else if (st == InvoiceStatus.PARTIALLY_PAID)
                                                partial += count;
                                        else if (st == InvoiceStatus.PAID)
                                                paid += count;
                                }
                        }

                        // Filter: Only show active customers (have balance, credit, or recent
                        // activity?)
                        // If strictly Debt Report -> Balance > 0?
                        // User asked for "Summaries". Showing even 0 balance is fine if they are
                        // wholesale.
                        // But let's keep previous logic: if list not empty.
                        // Actually, showing all Wholesale customers is safer for transparency.
                        summaries.add(new CustomerInvoiceSummary(
                                        c.getId(),
                                        c.getName(),
                                        totalBalance,
                                        unpaid,
                                        partial,
                                        paid,
                                        totalCredit));
                }

                // Retail Invoices (Recent)
                List<Invoice> retailInvoices = invoiceRepository
                                .findTop20BySalesOrderOrderTypeOrderByInvoiceDateDesc(
                                                com.mushroom.stockkeeper.model.CustomerType.RETAIL.name());

                model.addAttribute("summaries", summaries);
                model.addAttribute("retailInvoices", retailInvoices);
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
                model.addAttribute("creditNotes", invoice.getCreditNotes()); // Assuming lazy load works or transaction
                                                                             // is open
                return "invoices/detail";
        }
}
