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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

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
    public String index(@RequestParam(defaultValue = "wholesale") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q, // Search
            @RequestParam(required = false) String status, // For Retail Invoices
            Model model) {

        model.addAttribute("currentTab", tab);

        // --- Tab 1: Wholesale (Customers) ---
        // We always fetch a page of customers if we are on wholesale tab, or maybe just
        // defaults?
        // To avoid double query complexity, if tab=wholesale, we paginate customers.
        // If tab=retail, we paginate invoices.
        // But the view renders both tabs (one active, one hidden).
        // Ideally, we should use AJAX or separated views. But to keep it simple single
        // page:
        // We will fetch Page 0 for the inactive tab to populate it initially (or logic
        // to load on click).
        // Let's implement: "Current Tab paginated, Other Tab default 0/10".

        if ("retail".equalsIgnoreCase(tab)) {
            handleRetailTab(page, size, q, status, model);
            handleWholesaleTab(0, 5, null, model); // Preview for background tab
        } else {
            handleWholesaleTab(page, size, q, model);
            handleRetailTab(0, 5, null, null, model); // Preview for background tab
        }

        return "collections/index";
    }

    private void handleWholesaleTab(int page, int size, String q, Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), CustomerType.WHOLESALE));
            predicates.add(cb.isFalse(root.get("isHidden")));

            if (q != null && !q.trim().isEmpty()) {
                String likePattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("contactPerson")), likePattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Customer> customersPage = customerRepository.findAll(spec, pageable);
        List<Customer> customers = customersPage.getContent();

        // Calculate Balances for this PAGE only
        Map<Long, BigDecimal> balances = new HashMap<>();
        Map<Long, BigDecimal> credits = new HashMap<>();

        for (Customer c : customers) {
            BigDecimal bal = invoiceRepository.sumOutstandingBalanceByCustomer(c.getId());
            balances.put(c.getId(), bal != null ? bal : BigDecimal.ZERO);

            // This is inefficient (N+1-ish) but fine for paging size 10-50.
            // Optimized query exists but returns all customers.
            // We could filter the optimized query result in memory but that requires
            // fetching all.
            // Better to just sum for these 10 customers?
            // Actually, findWholesaleRemainingCredits() returns ALL.
            // Let's use simple repo calls for page items.

            // Credit fetch (dummy or repo method?)
            // We don't have sumCreditsByCustomer exposed cleanly in repo, let's use the
            // list method logic?
            // Or better, assume we don't have credits logic fully refactored yet, so use
            // existing map approach?
            // Problem: existing approach `findWholesaleRemainingCredits` fetches ALL.
            // If we have 1000 customers, it's okay for now.
            credits.put(c.getId(), BigDecimal.ZERO); // Placeholder if not found below
        }

        // Optimize: Fetch ALL credits/balances is actually faster than N queries if N
        // is large,
        // but for pagination N is small.
        // However, user existing logic used global aggregation.
        // Let's stick to global aggregation for balances/credits for now as dataset is
        // likely small (<1000).
        // If it grows, we optimize.

        List<Object[]> balancesData = invoiceRepository.findWholesaleOutstandingBalances();
        List<Object[]> creditsData = creditNoteRepository.findWholesaleRemainingCredits();

        // Convert to map
        Map<Long, BigDecimal> globalBalances = new HashMap<>();
        for (Object[] row : balancesData)
            globalBalances.put((Long) row[0], (BigDecimal) row[1]);

        Map<Long, BigDecimal> globalCredits = new HashMap<>();
        for (Object[] row : creditsData)
            globalCredits.put((Long) row[0], (BigDecimal) row[1]);

        // limit to current page customers
        for (Customer c : customers) {
            balances.put(c.getId(), globalBalances.getOrDefault(c.getId(), BigDecimal.ZERO));
            credits.put(c.getId(), globalCredits.getOrDefault(c.getId(), BigDecimal.ZERO));
        }

        model.addAttribute("customersPage", customersPage);
        model.addAttribute("customers", customers); // For table
        model.addAttribute("balances", balances);
        model.addAttribute("credits", credits);
    }

    private void handleRetailTab(int page, int size, String q, String status, Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "invoiceDate"));

        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Retail Only (Linked via Sales Order?)
            // Inherently we want Retail Invoices.
            // Invoice has Customer.
            predicates.add(cb.equal(root.get("customer").get("type"), CustomerType.RETAIL));

            if (q != null && !q.trim().isEmpty()) {
                String likePattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), likePattern),
                        cb.like(cb.lower(root.get("customer").get("name")), likePattern)));
            }

            if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
                try {
                    InvoiceStatus statusEnum = InvoiceStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (Exception e) {
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Invoice> retailPage = invoiceRepository.findAll(spec, pageable);
        model.addAttribute("retailPage", retailPage);
        model.addAttribute("retailInvoices", retailPage.getContent());
        model.addAttribute("currentStatus", status);
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
