package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReturnsService {

    private final InventoryUnitRepository unitRepository;
    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final PaymentRepository paymentRepository;

    public ReturnsService(InventoryUnitRepository unitRepository, InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository, PaymentRepository paymentRepository) {
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public CreditNote processReturn(String qrContent, String reason) throws Exception {
        String uuid = qrContent.startsWith("U:") ? qrContent.substring(2) : qrContent;
        InventoryUnit unit = unitRepository.findByUuid(uuid).orElseThrow(() -> new Exception("Unit not found"));

        if (unit.getStatus() != InventoryStatus.SOLD) {
            throw new Exception("Unit is not SOLD (Status: " + unit.getStatus() + ")");
        }

        SalesOrder so = unit.getSalesOrder();
        if (so == null) {
            throw new Exception("No Sales Order linked to unit");
        }

        // Find Invoice via SO
        Invoice invoice = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getSalesOrder().getId().equals(so.getId()))
                .findFirst()
                .orElseThrow(() -> new Exception("No Invoice found for this Order"));

        // Value per unit?
        // Assume simple division or fixed price. Invoice.total / SO.units?
        // If SO has 10 units and total 100, then 10 per unit.
        BigDecimal unitPrice = invoice.getTotalAmount().divide(new BigDecimal(so.getAllocatedUnits().size()), 2,
                java.math.RoundingMode.HALF_UP);

        // Update Unit Status
        unit.setStatus(InventoryStatus.RETURNED);
        unitRepository.save(unit);

        // Create Credit Note (Always create for tracking)
        CreditNote note = new CreditNote();
        note.setCustomer(invoice.getCustomer());
        note.setOriginalInvoice(invoice);
        note.setAmount(unitPrice);
        note.setNoteDate(LocalDate.now());
        note.setReason(reason != null ? reason : "Return: " + uuid);
        note.setNoteNumber("CN-" + System.currentTimeMillis());
        note.setUsed(true); // Default to used
        creditNoteRepository.save(note);

        // Adjust Logic based on Payment Status
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            // REFUND Logic: Create a negative payment
            Payment refund = new Payment();
            refund.setCustomer(invoice.getCustomer());
            refund.setAmount(unitPrice.negate()); // Negative amount
            refund.setPaymentDate(LocalDate.now());
            refund.setPaymentMethod(PaymentMethod.CASH); // Default to Cash refund or tracked elsewhere
            refund.setReferenceNumber("Refund for: " + uuid);
            paymentRepository.save(refund);

            // Note: We do NOT change invoice balance or status. It remains PAID.

        } else {
            // CREDIT/UNPAID Logic: Reduce balance
            invoice.setBalanceDue(invoice.getBalanceDue().subtract(unitPrice));
            invoiceRepository.save(invoice);
        }

        return note;
    }
}
