package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        BigDecimal totalUnits = new BigDecimal(so.getAllocatedUnits().size());
        BigDecimal unitPrice = invoice.getTotalAmount().divide(totalUnits, 2, java.math.RoundingMode.HALF_UP);

        // Calculate Tax Portion
        BigDecimal totalTax = invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal unitTax = BigDecimal.ZERO;
        if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
            unitTax = totalTax.divide(totalUnits, 2, java.math.RoundingMode.HALF_UP);
        }

        // Update Unit Status
        unit.setStatus(InventoryStatus.RETURNED);
        unitRepository.save(unit);

        // Create Credit Note
        CreditNote note = new CreditNote();
        note.setCustomer(invoice.getCustomer());
        note.setOriginalInvoice(invoice);
        note.setAmount(unitPrice);
        note.setTaxAmount(unitTax);
        note.setNoteDate(LocalDate.now());
        note.setReason(reason != null ? reason : "Return: " + uuid);
        note.setNoteNumber("CN-" + System.currentTimeMillis());

        // UNIFIED LOGIC: Always Issue Store Credit (Unused)
        // We do NOT auto-deduct from the invoice logic anymore.
        // The user must manually "Redeem" this credit against the invoice later if they
        // wish.
        note.setUsed(false);
        note.setRemainingAmount(unitPrice);

        creditNoteRepository.save(note);

        return note;
    }
}
