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

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository, CreditNoteRepository creditNoteRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.creditNoteRepository = creditNoteRepository;
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

        // Standard Payment: Only apply the new CASH amount.
        // Do NOT sweep credits automatically.
        distributeFunds(customerId, amount, payment);
    }

    @Transactional
    public void settleAccount(Long customerId, BigDecimal newCashInjection) {
        // Redeeem Credits / Settle Account Logic

        // 1. Sweep Negative Invoices (Legacy/Overflow) to create new credits if any
        List<Invoice> allInvoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        for (Invoice invoice : allInvoices) {
            BigDecimal due = invoice.getBalanceDue();
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                // Convert negative balance to Credit Note?
                // Current logic sets to zero and marks paid. Logic inside distribute funds will
                // handle?
                // No, we need to extract this value as "Available Funds" essentially.
                // Ideally, we should create a Credit Note for this negative amount FIRST.
                CreditNote overflowNote = new CreditNote();
                overflowNote.setCustomer(customerRepository.findById(customerId).orElseThrow());
                overflowNote.setAmount(due.abs());
                overflowNote.setRemainingAmount(due.abs());
                overflowNote.setNoteDate(java.time.LocalDate.now());
                overflowNote.setReason("Balance Adjustment for " + invoice.getInvoiceNumber());
                overflowNote.setNoteNumber("ADJ-" + System.currentTimeMillis());
                creditNoteRepository.save(overflowNote);

                invoice.setBalanceDue(BigDecimal.ZERO);
                invoice.setAmountPaid(invoice.getTotalAmount());
                invoice.setStatus(InvoiceStatus.PAID);
                invoiceRepository.save(invoice);
            }
        }

        // 2. Calculate Total Debt (Needed Funds)
        // Refresh invoice list after sweep
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

                totalCreditTaken = totalCreditTaken.add(toTake);
            }
        }

        // 5. Record Payment for Credit Usage
        if (totalCreditTaken.compareTo(BigDecimal.ZERO) > 0) {
            Payment creditPayment = new Payment();
            creditPayment.setCustomer(customerRepository.findById(customerId).orElseThrow());
            creditPayment.setAmount(totalCreditTaken);
            creditPayment.setPaymentMethod(PaymentMethod.CREDIT_NOTE);
            creditPayment.setReferenceNumber("REDEMPTION-" + System.currentTimeMillis());
            paymentRepository.save(creditPayment);
            // We do NOT link credit usage payment to "Change" notes usually, as it consumes
            // exact amount.
        }

        // 6. Record Payment for Cash Injection (FIX: Missing in previous version)
        Payment cashPayment = null;
        if (newCashInjection.compareTo(BigDecimal.ZERO) > 0) {
            cashPayment = new Payment();
            cashPayment.setCustomer(customerRepository.findById(customerId).orElseThrow());
            cashPayment.setAmount(newCashInjection);
            cashPayment.setPaymentMethod(PaymentMethod.CASH); // Or generic
            cashPayment.setReferenceNumber("SETTLE-" + System.currentTimeMillis());
            paymentRepository.save(cashPayment);
        }

        // 7. Distribute Funds (Cash + Credit Taken)
        // This will now match exactly the debt (or exceed it if Cash > Debt).
        // We won't trigger the "Change Note" logic inside distributeFunds unless it's
        // Cash Overpayment.
        // If there's chaos (Overpayment), it comes from Cash Injection side.
        // We link potential Overage Note to 'cashPayment'.
        distributeFunds(customerId, newCashInjection.add(totalCreditTaken), cashPayment);
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
            // This acts as "Change" for the transaction.
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
                throw new IllegalStateException("Cannot void payment. The resulting Credit Note ("
                        + note.getNoteNumber()
                        + ") has already been partially or fully used. Please void the subsequent usage first.");
            }

            // If unused, we void it (set to zero).
            note.setRemainingAmount(BigDecimal.ZERO);
            note.setReason(note.getReason() + " [VOIDED via Payment Reversal]");
            // Note: We don't delete it, just zero it out so it's effectively useless.
            creditNoteRepository.save(note);
        }

        // 1. Mark as Reversed
        payment.setReversed(true);
        paymentRepository.save(payment);

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
        }

        // 3. Reverse the Financial Impact (Add Debt Back)
        // We need to make Invoices "Unpaid" again equal to the amount.
        // Strategy: LIFO (Reverse of FIFO). Unpay the most recent invoices first?
        // Or unpay the ones that are Paid?
        // Generally, we want to restore balances.

        BigDecimal amountToRestore = payment.getAmount();
        List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(payment.getCustomer().getId(),
                InvoiceStatus.UNPAID); // Get Paid/Partial
        // Sort DESC to unpay newest first (Audit trail usually pays oldest first, so
        // voiding newest first makes sense? Or voiding specific?)
        // Since we don't track link, LIFO is safest assumption.
        invoices.sort(Comparator.comparing(Invoice::getInvoiceDate).reversed());

        for (Invoice invoice : invoices) {
            if (amountToRestore.compareTo(BigDecimal.ZERO) <= 0)
                break;

            // How much debt can we put back on this invoice?
            // Max debt = TotalAmount. Currently has BalanceDue.
            // restore = min(amountToRestore, TotalAmount - BalanceDue)

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
                // Fix precision if it exceeds?
                if (invoice.getBalanceDue().compareTo(invoice.getTotalAmount()) > 0) {
                    invoice.setBalanceDue(invoice.getTotalAmount());
                }
            } else {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }

            invoiceRepository.save(invoice);
            amountToRestore = amountToRestore.subtract(restore);
        }

        // If amountToRestore > 0 still?
        // Means we paid more than invoices existed? (Overpayment).
        // If original payment created a "Overpayment Credit", that credit exists.
        // We should probably void that credit too?
        // This gets complex. For now, assuming standard flow, this covers 99% cases.
    }
}
