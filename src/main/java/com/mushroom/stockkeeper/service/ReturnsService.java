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

    public ReturnsService(InventoryUnitRepository unitRepository, InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository) {
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
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

        // Create Credit Note
        CreditNote note = new CreditNote();
        note.setCustomer(invoice.getCustomer());
        note.setOriginalInvoice(invoice);
        note.setAmount(unitPrice);
        note.setNoteDate(LocalDate.now());
        note.setReason(reason != null ? reason : "Return: " + uuid);
        note.setNoteNumber("CN-" + System.currentTimeMillis());
        note.setUsed(true); // Applied immediately to balance
        creditNoteRepository.save(note);

        // Adjust Invoice Balance
        // If BalanceDue > 0, reduce BalanceDue.
        // If BalanceDue == 0 (Paid), we technically owe money.
        // The prompt says: "reduce the Invoice.balanceDue".
        // If already paid, balance becomes negative? Or we just create CN.

        invoice.setBalanceDue(invoice.getBalanceDue().subtract(unitPrice));

        // If balance goes negative (Overpaid), it means we owe customer.
        // Invoice status logic usually handles >= 0.
        // Let's allow negative balance to indicate credit for MVP.
        invoiceRepository.save(invoice);

        return note;
    }
}
