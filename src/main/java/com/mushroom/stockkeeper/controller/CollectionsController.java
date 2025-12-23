package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.TransactionDTO;
import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.CreditNoteRepository;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import com.mushroom.stockkeeper.repository.InvoiceRepository;
import com.mushroom.stockkeeper.repository.PaymentRepository;
import com.mushroom.stockkeeper.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/collections")
public class CollectionsController {

    private final PaymentService paymentService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PaymentRepository paymentRepository;

    public CollectionsController(PaymentService paymentService, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository,
            CreditNoteRepository creditNoteRepository,
            PaymentRepository paymentRepository) {
        this.paymentService = paymentService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    public String index(Model model) {
        // Aging Report Logic: WHOLESALE Customers Only
        List<Customer> customers = customerRepository
                .findByTypeAndIsHiddenFalse(CustomerType.WHOLESALE);

        // Fetch aggregated data efficiently
        List<Object[]> balancesData = invoiceRepository.findWholesaleOutstandingBalances(); // [customerId, totalDue,
                                                                                            // unpaidCount]
        List<Object[]> creditsData = creditNoteRepository.findWholesaleRemainingCredits(); // [customerId, totalCredit]

        // Map aggregated data
        Map<Long, BigDecimal> balances = new HashMap<>();
        // User requested Gross Balance (Due only).
        // Logic below stores Total Due in 'balances' map.

        Map<Long, BigDecimal> credits = new HashMap<>(); // Just Credits

        // Populate Maps from DB Data
        for (Object[] row : balancesData) {
            balances.put((Long) row[0], (BigDecimal) row[1]);
        }
        for (Object[] row : creditsData) {
            credits.put((Long) row[0], (BigDecimal) row[1]);
        }

        // Fill gaps for customers with 0 balance/credit to ensure map keys exist if
        // needed
        for (Customer c : customers) {
            if (!balances.containsKey(c.getId()))
                balances.put(c.getId(), BigDecimal.ZERO);
            if (!credits.containsKey(c.getId()))
                credits.put(c.getId(), BigDecimal.ZERO);
        }

        // Retail Sales Segment (Recent Invoices)
        List<Invoice> retailInvoices = invoiceRepository.findTop20BySalesOrderOrderTypeOrderByInvoiceDateDesc(
                CustomerType.RETAIL.name());

        model.addAttribute("customers", customers);
        model.addAttribute("balances", balances);
        model.addAttribute("credits", credits);
        model.addAttribute("retailInvoices", retailInvoices);
        return "collections/index";
    }

    @GetMapping("/payment")
    public String paymentForm(Model model) {
        // Only allow recording payments for Wholesale customers (Visible)
        List<Customer> customers = customerRepository
                .findByTypeAndIsHiddenFalse(CustomerType.WHOLESALE);
        Map<Long, BigDecimal> balances = new HashMap<>();

        for (Customer c : customers) {
            List<Invoice> unpaid = invoiceRepository.findByCustomerIdAndStatusNot(c.getId(), InvoiceStatus.PAID);
            BigDecimal totalDue = unpaid.stream()
                    .map(Invoice::getBalanceDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // We usually pay against Dues, ignoring credits in this form intentionally
            // until they settled.
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
            RedirectAttributes redirectAttributes) {

        // Validate Amount
        // Allow payment even if amount > due (creates credit)
        // But maybe warn? For now enterprise logic handles overpayment.

        paymentService.recordPayment(customerId, amount, method, reference);
        redirectAttributes.addFlashAttribute("success", "Payment recorded successfully.");
        return "redirect:/collections";
    }

    @PostMapping("/redeem")
    public String redeemCredits(@RequestParam Long customerId,
            RedirectAttributes redirectAttributes) {
        // Trigger generic settlement with 0 cash injection
        paymentService.settleAccount(customerId, BigDecimal.ZERO);
        redirectAttributes.addFlashAttribute("success", "Credits redeemed and applied to outstanding balance.");
        return "redirect:/collections";
    }

    @PostMapping("/payment/void")
    @PreAuthorize("hasRole('ADMIN')")
    public String voidPayment(@RequestParam Long paymentId,
            RedirectAttributes redirectAttributes) {
        paymentService.voidPayment(paymentId);
        redirectAttributes.addFlashAttribute("success", "Payment reversed.");
        return "redirect:/collections";
    }

    @GetMapping("/statement")
    public String statement(@RequestParam Long customerId, Model model) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();

        List<TransactionDTO> transactions = new ArrayList<>();

        // 1. Invoices (Debits)
        List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, null); // All statuses
        for (Invoice inv : invoices) {
            transactions.add(new TransactionDTO(
                    inv.getInvoiceDate(),
                    "INVOICE",
                    inv.getInvoiceNumber(),
                    "Sales Order",
                    inv.getTotalAmount(),
                    BigDecimal.ZERO));
        }

        // 2. Payments (Credits)
        List<Payment> payments = paymentRepository.findByCustomerId(customerId);
        for (Payment p : payments) {
            String desc = p.isReversed() ? "VOIDED" : "";
            transactions.add(new TransactionDTO(
                    p.getPaymentDate() != null ? p.getPaymentDate() : p.getCreatedAt().toLocalDate(),
                    "PAYMENT",
                    p.getReferenceNumber(),
                    desc,
                    BigDecimal.ZERO,
                    p.getAmount(),
                    p.getId(),
                    p.isReversed()));

            // Logic: If Reversed, the Payment exists (Credit). We need a Reversal (Debit)
            // to offset it.
            if (p.isReversed()) {
                transactions.add(new TransactionDTO(
                        p.getCreatedAt().toLocalDate(), // Reversal happens at create time of void? Don't track void
                                                        // date. Use today?
                        "REVERSAL",
                        "REV-" + p.getReferenceNumber(),
                        "Void Reversal",
                        p.getAmount(),
                        BigDecimal.ZERO));
            }
        }

        // 3. Sort
        transactions.sort(Comparator.comparing(TransactionDTO::getDate)
                .thenComparing(TransactionDTO::getReference));

        // 4. Running Balance
        BigDecimal balance = BigDecimal.ZERO;
        for (TransactionDTO t : transactions) {
            balance = balance.add(t.getDebit()).subtract(t.getCredit());
            t.setBalance(balance);
        }

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactions);
        return "collections/statement";
    }
}
