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
    private final com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository;
    private final com.mushroom.stockkeeper.repository.PaymentRepository paymentRepository;

    public CollectionsController(PaymentService paymentService, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository,
            com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository,
            com.mushroom.stockkeeper.repository.PaymentRepository paymentRepository) {
        this.paymentService = paymentService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    public String index(Model model) {
        // Aging Report Logic: Map<Customer, TotalDue>
        List<Customer> customers = customerRepository.findAll();
        Map<Long, BigDecimal> balances = new HashMap<>(); // Net Balance (Due - Credit)
        Map<Long, BigDecimal> credits = new HashMap<>(); // Just Credits

        for (Customer c : customers) {
            // Dues
            List<Invoice> unpaid = invoiceRepository.findByCustomerIdAndStatusNot(c.getId(), InvoiceStatus.PAID);
            BigDecimal totalDue = unpaid.stream()
                    .map(Invoice::getBalanceDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Credits (Notes)
            List<com.mushroom.stockkeeper.model.CreditNote> notes = creditNoteRepository.findAll().stream()
                    .filter(n -> n.getCustomer().getId().equals(c.getId()))
                    .filter(n -> n.getRemainingAmount() != null
                            && n.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            BigDecimal totalCredit = notes.stream()
                    .map(com.mushroom.stockkeeper.model.CreditNote::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            credits.put(c.getId(), totalCredit);

            // Net Balance for Display: Due - Credit
            // Update: User requested Gross Balance (Due only). Credits shown separately.
            balances.put(c.getId(), totalDue);
        }

        model.addAttribute("customers", customers);
        model.addAttribute("balances", balances);
        model.addAttribute("credits", credits);
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
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        // Validate Amount
        // Allow payment even if amount > due (creates credit)
        // But maybe warn? For now enterprise logic handles overpayment.

        paymentService.recordPayment(customerId, amount, method, reference);
        redirectAttributes.addFlashAttribute("success", "Payment recorded successfully.");
        return "redirect:/collections";
    }

    @PostMapping("/redeem")
    public String redeemCredits(@RequestParam Long customerId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        // Trigger generic settlement with 0 cash injection
        paymentService.settleAccount(customerId, BigDecimal.ZERO);
        redirectAttributes.addFlashAttribute("success", "Credits redeemed and applied to outstanding balance.");
        return "redirect:/collections";
    }

    @PostMapping("/payment/void")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public String voidPayment(@RequestParam Long paymentId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        paymentService.voidPayment(paymentId);
        redirectAttributes.addFlashAttribute("success", "Payment reversed.");
        return "redirect:/collections";
    }

    @GetMapping("/statement")
    public String statement(@RequestParam Long customerId, Model model) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();

        List<com.mushroom.stockkeeper.dto.TransactionDTO> transactions = new java.util.ArrayList<>();

        // 1. Invoices (Debits)
        List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, null); // All statuses
        for (Invoice inv : invoices) {
            transactions.add(new com.mushroom.stockkeeper.dto.TransactionDTO(
                    inv.getInvoiceDate(),
                    "INVOICE",
                    inv.getInvoiceNumber(),
                    "Sales Order",
                    inv.getTotalAmount(),
                    BigDecimal.ZERO));
        }

        // 2. Payments (Credits)
        List<com.mushroom.stockkeeper.model.Payment> payments = paymentRepository.findByCustomerId(customerId);
        for (com.mushroom.stockkeeper.model.Payment p : payments) {
            String desc = p.isReversed() ? "VOIDED" : "";
            transactions.add(new com.mushroom.stockkeeper.dto.TransactionDTO(
                    p.getPaymentDate() != null ? p.getPaymentDate() : p.getCreatedAt().toLocalDate(),
                    "PAYMENT",
                    p.getReferenceNumber(),
                    desc,
                    BigDecimal.ZERO,
                    p.getAmount(),
                    p.getId(),
                    p.isReversed())); // If reversed, we need to show the reversal?
            // Logic: If Reversed, the Payment exists (Credit). We need a Reversal (Debit)
            // to offset it.
            // OR: We just show it as effective 0?
            // Standard: Show Payment (Cr), then Reversal (Dr).
            if (p.isReversed()) {
                transactions.add(new com.mushroom.stockkeeper.dto.TransactionDTO(
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
        transactions.sort(java.util.Comparator.comparing(com.mushroom.stockkeeper.dto.TransactionDTO::getDate)
                .thenComparing(com.mushroom.stockkeeper.dto.TransactionDTO::getReference));

        // 4. Running Balance
        BigDecimal balance = BigDecimal.ZERO;
        for (com.mushroom.stockkeeper.dto.TransactionDTO t : transactions) {
            balance = balance.add(t.getDebit()).subtract(t.getCredit());
            t.setBalance(balance);
        }

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactions);
        return "collections/statement";
    }
}
