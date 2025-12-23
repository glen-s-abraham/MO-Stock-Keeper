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

    private final AuditService auditService;

    public ReturnsService(InventoryUnitRepository unitRepository, InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository, AuditService auditService) {
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.auditService = auditService;
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
        // Use the actual price the unit was sold for
        BigDecimal unitPrice = unit.getSoldPrice();
        if (unitPrice == null) {
            // Fallback if legacy unit has no price (should not happen with new validator)
            BigDecimal totalUnits = new BigDecimal(so.getAllocatedUnits().size());
            unitPrice = invoice.getTotalAmount().divide(totalUnits, 2, java.math.RoundingMode.HALF_UP);
        }

        // Calculate Tax Portion Proportional to Price
        // (Unit Price / Invoice Total) * Total Tax
        BigDecimal totalTax = invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal unitTax = BigDecimal.ZERO;
        if (totalTax.compareTo(BigDecimal.ZERO) > 0 && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = unitPrice.divide(invoice.getTotalAmount(), 6, java.math.RoundingMode.HALF_UP);
            unitTax = totalTax.multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP);
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

        // UNIFIED LOGIC:
        // 1. Walk-in / Guest / Saved Retail -> Immediate Refund (Used=true, Cash Out)
        // 2. Account Customers (Wholesale) -> Store Credit (Used=false)

        boolean isRetail = invoice.getCustomer().getType() == CustomerType.RETAIL
                || invoice.getCustomer().isHidden();

        if (isRetail) {
            note.setUsed(true);
            note.setRemainingAmount(BigDecimal.ZERO);
            note.setReason(note.getReason() + " (Cash Refund)");
        } else {
            note.setUsed(false);
            note.setRemainingAmount(unitPrice);
        }

        creditNoteRepository.save(note);

        return note;
    }

    @Transactional
    public void restockUnit(Long unitId) throws Exception {
        InventoryUnit unit = unitRepository.findById(unitId).orElseThrow();
        if (unit.getStatus() != InventoryStatus.RETURNED) {
            throw new Exception("Only RETURNED units can be restocked.");
        }
        unit.setStatus(InventoryStatus.AVAILABLE);
        unit.setSalesOrder(null);
        unit.setSoldPrice(null);
        unitRepository.save(unit);
        auditService.log("UNIT_RESTOCKED", "Restocked Unit " + unit.getUuid() + " to Available Inventory");
    }

    @Transactional
    public void spoilUnit(Long unitId) throws Exception {
        InventoryUnit unit = unitRepository.findById(unitId).orElseThrow();
        if (unit.getStatus() != InventoryStatus.RETURNED) {
            throw new Exception("Only RETURNED units can be marked as spoiled (from Returns Module).");
        }
        unit.setStatus(InventoryStatus.SPOILED);
        // We keep SalesOrder link for history? Or unlink?
        // Usually SPOILED means dead stock. Unlink makes sense to remove from "Active
        // Sales" scope but
        // for traceability "Why was it spoiled?" -> "Because it was returned damaged".
        // Let's Keep SalesOrder link if possible?
        // Actually InventoryUnit.salesOrder is for "Current Allocation".
        // If we spoil it, it's no longer part of that order's fulfillment (technically
        // returned).
        // Let's unlink to be clean.
        unit.setSalesOrder(null);
        unitRepository.save(unit);
        auditService.log("UNIT_SPOILED", "Marked Returned Unit " + unit.getUuid() + " as SPOILED");
    }
}
