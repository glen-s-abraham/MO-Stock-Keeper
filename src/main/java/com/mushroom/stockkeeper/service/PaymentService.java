package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.InvoiceStatus;
import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
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

        // 2. FIFO Allocation (Waterfall)
        BigDecimal remainingAmount = amount;

        // Find unpaid invoices for customer, ordered by Date ASC
        List<Invoice> invoices = invoiceRepository.findByCustomerIdAndStatusNot(customerId, InvoiceStatus.PAID);
        invoices.sort(Comparator.comparing(Invoice::getInvoiceDate)); // Ensure oldest first

        for (Invoice invoice : invoices) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0)
                break;

            BigDecimal due = invoice.getBalanceDue();
            BigDecimal allocation = due.min(remainingAmount); // Min(Due, Remaining)

            // Update Invoice
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

        // If remainingAmount > 0, calculate as Credit or Overpayment?
        // Ideally create a Credit Note or store as "Unapplied Cash".
        // For MVP, we just leave it recorded in Payment but not allocated?
        // User asked for "Waterfall Allocation".
    }
}
