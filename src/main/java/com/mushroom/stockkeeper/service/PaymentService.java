package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.InvoiceStatus;
import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository, CreditNoteRepository creditNoteRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void recordPayment(Long customerId, BigDecimal amount, PaymentMethod method, String reference) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();

        // 1. Record Payment
        Payment payment = new Payment();
        payment.setCustomer(customer);
        payment.setAmount(amount);
        payment.setPaymentMethod(method);
        payment.setReferenceNumber(reference);
        paymentRepository.save(payment);

        auditService.log("PAYMENT_RECEIVED", String.format("Recorded Payment %s of %s from %s via %s",
                reference, amount, customer.getName(), method));

        // Standard Payment: Only apply the new CASH amount.
        // Do NOT sweep credits automatically.
        distributeFunds(customerId, amount, payment);
    }

    @Transactional
    public void settleAccount(Long customerId, BigDecimal newCashInjection) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        // Redeeem Credits / Settle Account Logic

        // 1. Sweep Negative Invoices (Legacy/Overflow) to create new credits if any
        List<Invoice> allInvoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        for (Invoice invoice : allInvoices) {
            BigDecimal due = invoice.getBalanceDue();
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                // Convert negative balance to Credit Note
                CreditNote overflowNote = new CreditNote();
                overflowNote.setCustomer(customer);
                overflowNote.setAmount(due.abs());
                overflowNote.setRemainingAmount(due.abs());
                overflowNote.setNoteDate(java.time.LocalDate.now());
                overflowNote.setReason("Balance Adjustment for " + invoice.getInvoiceNumber());
                overflowNote.setNoteNumber("ADJ-" + System.currentTimeMillis());
                creditNoteRepository.save(overflowNote);

                auditService.log("CREDIT_GENERATED", "Generated Adjustment Note " + overflowNote.getNoteNumber()
                        + " from Negative Invoice " + invoice.getInvoiceNumber());

                invoice.setBalanceDue(BigDecimal.ZERO);
                invoice.setAmountPaid(invoice.getTotalAmount());
                invoice.setStatus(InvoiceStatus.PAID);
                invoiceRepository.save(invoice);
            }
        }

        // 2. Calculate Total Debt (Needed Funds)
        List<Invoice> unpaidInvoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        BigDecimal totalDebt = unpaidInvoices.stream()
                .map(Invoice::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Determine how much Credit to Use
        BigDecimal creditNeeded = BigDecimal.ZERO;
        if (newCashInjection.compareTo(totalDebt) < 0) {
            creditNeeded = totalDebt.subtract(newCashInjection);
        }

        // 4. Gather Credits and Consume ONLY what is needed (In-Place)
        BigDecimal totalCreditTaken = BigDecimal.ZERO;

        if (creditNeeded.compareTo(BigDecimal.ZERO) > 0) {
            List<CreditNote> notes = creditNoteRepository.findAll().stream()
                    .filter(n -> n.getCustomer().getId().equals(customerId))
                    .filter(n -> n.getRemainingAmount() != null
                            && n.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                    .sorted(Comparator.comparing(CreditNote::getNoteDate)) // FIFO usage
                    .collect(java.util.stream.Collectors.toList());

            for (CreditNote note : notes) {
                if (totalCreditTaken.compareTo(creditNeeded) >= 0)
                    break; // Have enough

                BigDecimal availableOnNote = note.getRemainingAmount();
                BigDecimal stillNeed = creditNeeded.subtract(totalCreditTaken);
                BigDecimal toTake = availableOnNote.min(stillNeed);

                // Update Note In-Place (No "Change Note" created)
                note.setRemainingAmount(availableOnNote.subtract(toTake));
                if (note.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
                    note.setUsed(true); // Fully used
                }
                creditNoteRepository.save(note);

                auditService.log("CREDIT_UTILIZED",
                        String.format("Used %s from Note %s for Settlement", toTake, note.getNoteNumber()));

                totalCreditTaken = totalCreditTaken.add(toTake);
            }
        }

        // 5. Record Payment for Credit Usage
        Payment creditPayment = null;
        if (totalCreditTaken.compareTo(BigDecimal.ZERO) > 0) {
            creditPayment = new Payment();
            creditPayment.setCustomer(customer);
            creditPayment.setAmount(totalCreditTaken);
            creditPayment.setPaymentMethod(PaymentMethod.CREDIT_NOTE);
            creditPayment.setReferenceNumber("REDEMPTION-" + System.currentTimeMillis());
            paymentRepository.save(creditPayment);

            auditService.log("SETTLEMENT_CREDIT", "Redeemed Total Credit: " + totalCreditTaken);
        }

        // 6. Record Payment for Cash Injection
        Payment cashPayment = null;
        if (newCashInjection.compareTo(BigDecimal.ZERO) > 0) {
            cashPayment = new Payment();
            cashPayment.setCustomer(customer);
            cashPayment.setAmount(newCashInjection);
            cashPayment.setPaymentMethod(PaymentMethod.CASH); // Or generic
            cashPayment.setReferenceNumber("SETTLE-" + System.currentTimeMillis());
            paymentRepository.save(cashPayment);

            auditService.log("SETTLEMENT_CASH", "Settlement Cash Injection: " + newCashInjection);
        }

        // 7. Distribute Funds - SPLIT EXECUTION
        // Apply Credits First
        if (creditPayment != null) {
            distributeFunds(customerId, totalCreditTaken, creditPayment);
        }

        // Apply Cash Second
        if (cashPayment != null) {
            distributeFunds(customerId, newCashInjection, cashPayment);
        }
    }

    private void distributeFunds(Long customerId, BigDecimal amount, Payment sourcePayment) {
        List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        invoices.sort(Comparator.comparing(Invoice::getInvoiceDate));

        BigDecimal remainingAmount = amount;

        for (Invoice invoice : invoices) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0)
                break;

            BigDecimal due = invoice.getBalanceDue();
            if (due.compareTo(BigDecimal.ZERO) <= 0)
                continue;

            BigDecimal allocation = due.min(remainingAmount);

            // Record Strict Allocation
            if (sourcePayment != null && allocation.compareTo(BigDecimal.ZERO) > 0) {
                PaymentAllocation pa = new PaymentAllocation();
                pa.setPayment(sourcePayment);
                pa.setInvoice(invoice);
                pa.setAmount(allocation);
                paymentAllocationRepository.save(pa);
            }

            invoice.setAmountPaid(invoice.getAmountPaid().add(allocation));
            invoice.setBalanceDue(invoice.getBalanceDue().subtract(allocation));

            if (invoice.getBalanceDue().compareTo(BigDecimal.ZERO) == 0) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }
            invoiceRepository.save(invoice);
            remainingAmount = remainingAmount.subtract(allocation);
        }

        // Handle Overpayment (Excess Credit)
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Create a NEW Credit Note for the remaining balance.
            CreditNote unapplied = new CreditNote();
            unapplied.setCustomer(customerRepository.findById(customerId).orElseThrow());
            unapplied.setAmount(remainingAmount);
            unapplied.setRemainingAmount(remainingAmount);
            unapplied.setNoteDate(java.time.LocalDate.now());
            unapplied.setNoteNumber("PAY-REM-" + System.currentTimeMillis());
            unapplied.setReason("Unapplied Payment/Credit Net Balance");
            // Integrity Link
            if (sourcePayment != null) {
                unapplied.setGeneratedFromPayment(sourcePayment);
            }
            creditNoteRepository.save(unapplied);

            auditService.log("CREDIT_GENERATED",
                    "Overpayment/Surplus generated Note " + unapplied.getNoteNumber() + " of value " + remainingAmount);
        }
    }

    @Transactional
    public void voidPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (payment.isReversed())
            return;

        // Integrity Check: Did this payment generate a Credit Note?
        Optional<CreditNote> generatedNote = creditNoteRepository.findByGeneratedFromPayment(payment);
        if (generatedNote.isPresent()) {
            CreditNote note = generatedNote.get();
            // Is it used?
            // If remaining < amount, it's partially or fully used.
            if (note.getRemainingAmount().compareTo(note.getAmount()) < 0) {
                throw new IllegalStateException("Security Block: This payment generated Credit Note "
                        + note.getNoteNumber()
                        + " which has already been spent. You must void the usages of that credit note first.");
            }

            // If unused, we void it (set to zero).
            note.setRemainingAmount(BigDecimal.ZERO);
            note.setReason(note.getReason() + " [VOIDED via Payment Reversal]");
            creditNoteRepository.save(note);

            auditService.log("VOID_CREDIT", "Voided associated unused Credit Note: " + note.getNoteNumber());
        }

        // 1. Mark as Reversed
        payment.setReversed(true);
        paymentRepository.save(payment);

        auditService.log("PAYMENT_VOIDED",
                "Voided Payment " + payment.getReferenceNumber() + " of " + payment.getAmount());

        // 2. Handle Credti Note Reversal specifically
        if (payment.getPaymentMethod() == PaymentMethod.CREDIT_NOTE) {
            // If we void a credit redemption, we give the credit back.
            // Simplest way: Issue a new Credit Note.
            CreditNote reversalNote = new CreditNote();
            reversalNote.setCustomer(payment.getCustomer());
            reversalNote.setAmount(payment.getAmount());
            reversalNote.setRemainingAmount(payment.getAmount());
            reversalNote.setNoteDate(java.time.LocalDate.now());
            reversalNote.setReason("Reversal of Redemption " + payment.getReferenceNumber());
            reversalNote.setNoteNumber("REV-" + System.currentTimeMillis());
            creditNoteRepository.save(reversalNote);

            auditService.log("CREDIT_REFUND",
                    "Refunded Credit via Note " + reversalNote.getNoteNumber() + " due to voided redemption");
        }

        // 3. Reverse the Financial Impact (Add Debt Back)
        // 3. Reverse the Financial Impact (Add Debt Back)
        // PRECISE REVERSAL via Allocations
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPayment(payment);

        if (!allocations.isEmpty()) {
            for (PaymentAllocation pa : allocations) {
                Invoice invoice = pa.getInvoice();
                BigDecimal restore = pa.getAmount();

                invoice.setBalanceDue(invoice.getBalanceDue().add(restore));
                invoice.setAmountPaid(invoice.getAmountPaid().subtract(restore));

                // Recalculate Status
                if (invoice.getBalanceDue().compareTo(BigDecimal.ZERO) == 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                } else if (invoice.getBalanceDue().compareTo(invoice.getTotalAmount()) >= 0) {
                    invoice.setStatus(InvoiceStatus.UNPAID);
                } else {
                    invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
                }
                invoiceRepository.save(invoice);
                // We don't delete the allocation record; keeping it as history of what WAS paid
                // is fine,
                // or we could mark it voids. Since Payment is reversed, the allocation is
                // implicitly void.
            }
        } else {
            // FALLBACK: Legacy "Debt Shifting" for old payments
            BigDecimal amountToRestore = payment.getAmount();
            List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(payment.getCustomer().getId(),
                    InvoiceStatus.UNPAID); // Get Paid/Partial

            invoices.sort(Comparator.comparing(Invoice::getInvoiceDate).reversed());

            for (Invoice invoice : invoices) {
                if (amountToRestore.compareTo(BigDecimal.ZERO) <= 0)
                    break;

                BigDecimal alreadyPaid = invoice.getTotalAmount().subtract(invoice.getBalanceDue());
                if (alreadyPaid.compareTo(BigDecimal.ZERO) <= 0)
                    continue;

                BigDecimal restore = amountToRestore.min(alreadyPaid);

                invoice.setBalanceDue(invoice.getBalanceDue().add(restore));
                invoice.setAmountPaid(invoice.getAmountPaid().subtract(restore));

                if (invoice.getBalanceDue().compareTo(BigDecimal.ZERO) == 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                } else if (invoice.getBalanceDue().compareTo(invoice.getTotalAmount()) >= 0) {
                    // Fully Unpaid
                    invoice.setStatus(InvoiceStatus.UNPAID);
                    if (invoice.getBalanceDue().compareTo(invoice.getTotalAmount()) > 0) {
                        invoice.setBalanceDue(invoice.getTotalAmount());
                    }
                } else {
                    invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
                }

                invoiceRepository.save(invoice);
                amountToRestore = amountToRestore.subtract(restore);
            }

            if (amountToRestore.compareTo(BigDecimal.ZERO) > 0) {
                auditService.log("VOID_WARNING", "Voided payment " + payment.getReferenceNumber()
                        + " exceeded restorable invoice debt by " + amountToRestore);
            }
        }
    }
}
