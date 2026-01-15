package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
// import java.util.UUID;

@Service
public class SalesService {

    private final SalesOrderRepository orderRepository;
    private final InventoryUnitRepository unitRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final AuditService auditService;

    public SalesService(SalesOrderRepository orderRepository, InventoryUnitRepository unitRepository,
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
            CreditNoteRepository creditNoteRepository, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SalesOrder createOrder(Customer customer, String orderType, String paymentMethod) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setOrderDate(LocalDate.now());
        so.setStatus(SalesOrderStatus.DRAFT);
        // Robust Order Number: SO-UUID (first 8 chars) or similar
        so.setOrderNumber("SO-" + java.util.UUID.randomUUID().toString().substring(0, 13).toUpperCase());

        // New Fields
        so.setOrderType(orderType);
        so.setPaymentMethod(paymentMethod);

        return orderRepository.save(so);
    }

    @Transactional
    public void allocateUnit(Long orderId, String qrContent) throws Exception {
        SalesOrder so = orderRepository.findById(orderId).orElseThrow(() -> new Exception("Order not found"));

        if (so.getStatus() == SalesOrderStatus.INVOICED || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new Exception("Cannot modify finalized order.");
        }

        // Parse QR. Format "U:UUID" or just "U:UUID"
        // Let's assume scanner reads "U:abc-123"
        String uuid = qrContent.startsWith("U:") ? qrContent.substring(2) : qrContent;

        // CONCURRENCY FIX: Use Pessimistic Lock
        InventoryUnit unit = unitRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new Exception("Unit not found: " + uuid));

        if (unit.getStatus() != InventoryStatus.AVAILABLE) {
            // Idempotency: If already in THIS order, return success
            if (unit.getStatus() == InventoryStatus.ALLOCATED && unit.getSalesOrder() != null
                    && unit.getSalesOrder().getId().equals(orderId)) {
                return; // Already added, ignore
            }
            throw new Exception("Unit " + uuid + " is not AVAILABLE (Status: " + unit.getStatus() + ")");
        }

        // Expiry Check
        if (unit.getBatch().getExpiryDate() != null && unit.getBatch().getExpiryDate().isBefore(LocalDate.now())) {
            throw new Exception(
                    "Unit " + uuid + " is EXPIRED (Expiry: " + unit.getBatch().getExpiryDate() + "). Cannot sell.");
        }

        // Link
        unit.setSalesOrder(so);
        unit.setStatus(InventoryStatus.ALLOCATED);

        // Auto-Apply Pricing based on Order Type
        BigDecimal price = BigDecimal.ZERO;
        if (unit.getBatch() != null && unit.getBatch().getProduct() != null) {
            Product product = unit.getBatch().getProduct();
            // Check if Wholesale
            // We can check orderType OR customer type. OrderType is explicit.
            boolean isWholesale = "WHOLESALE".equalsIgnoreCase(so.getOrderType());

            if (isWholesale) {
                price = product.getWholesalePrice() != null ? product.getWholesalePrice() : BigDecimal.ZERO;
            } else {
                // Retail Default
                price = product.getRetailPrice() != null ? product.getRetailPrice() : BigDecimal.ZERO;
            }
        }
        unit.setSoldPrice(price);

        unitRepository.save(unit);

        // Update SO status if needed (e.g. to Picking)
    }

    @Transactional
    public void removeUnit(Long unitId) throws Exception {
        InventoryUnit unit = unitRepository.findById(unitId).orElseThrow();
        if (unit.getSalesOrder() != null) {
            SalesOrder so = unit.getSalesOrder();
            if (so.getStatus() == SalesOrderStatus.INVOICED || so.getStatus() == SalesOrderStatus.CANCELLED) {
                throw new Exception("Cannot modify finalized order.");
            }
        }

        if (unit.getStatus() == InventoryStatus.ALLOCATED) {
            unit.setSalesOrder(null);
            unit.setStatus(InventoryStatus.AVAILABLE);
            unitRepository.save(unit);
        }
    }

    @Transactional
    public void updateProductPrice(Long orderId, Long productId, BigDecimal price) throws Exception {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new Exception("Price cannot be negative.");
        }

        SalesOrder so = orderRepository.findById(orderId).orElseThrow();

        if (so.getStatus() == SalesOrderStatus.INVOICED || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new Exception("Cannot modify finalized order.");
        }

        for (InventoryUnit unit : so.getAllocatedUnits()) {
            if (unit.getBatch().getProduct().getId().equals(productId)) {
                BigDecimal oldPrice = unit.getSoldPrice();
                unit.setSoldPrice(price);
                unitRepository.save(unit);
                // Detail Audit can be noisy, but price change is critical
                auditService.log("UPDATE_PRICE",
                        "Order: " + orderId + ", Unit: " + unit.getUuid() + ", Old: " + oldPrice + ", New: " + price);
            }
        }
    }

    @Transactional
    public void updateOrderDiscount(Long orderId, BigDecimal discountPercentage) throws Exception {
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) < 0
                || discountPercentage.compareTo(new BigDecimal(100)) > 0) {
            throw new Exception("Discount percentage must be between 0 and 100.");
        }

        SalesOrder so = orderRepository.findById(orderId).orElseThrow();
        if (so.getStatus() == SalesOrderStatus.INVOICED || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new Exception("Cannot modify finalized order.");
        }

        so.setDiscountPercentage(discountPercentage);
        orderRepository.save(so);
    }

    @Transactional
    public Invoice finalizeOrder(Long orderId, boolean isPaid, String paymentMethodStr) throws Exception {
        SalesOrder so = orderRepository.findById(orderId).orElseThrow();

        if (so.getStatus() == SalesOrderStatus.INVOICED) {
            throw new Exception("Order is already invoiced.");
        }

        if (so.getAllocatedUnits().isEmpty()) {
            throw new Exception("Cannot finalize empty order");
        }

        // Validate prices set for all units
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            if (unit.getSoldPrice() == null || unit.getSoldPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new Exception("Price not set for unit: " + unit.getUuid() + " ("
                        + unit.getBatch().getProduct().getName() + ")");
            }
        }

        // Calculate Total
        BigDecimal subTotal = BigDecimal.ZERO;
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            subTotal = subTotal.add(unit.getSoldPrice());
        }

        BigDecimal discountPercentage = so.getDiscountPercentage() != null ? so.getDiscountPercentage()
                : BigDecimal.ZERO;
        // Calc Discount Value
        BigDecimal discountValue = subTotal.multiply(discountPercentage).divide(new BigDecimal(100));

        BigDecimal total = subTotal.subtract(discountValue);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new Exception("Total cannot be negative");
        }

        // Validate Validation for Hidden/Guest OR Retail Customers
        boolean isRetail = so.getCustomer().getType() == CustomerType.RETAIL
                || CustomerType.RETAIL.name().equalsIgnoreCase(so.getOrderType())
                || so.getCustomer().isHidden();

        if (isRetail && !isPaid) {
            throw new Exception("Retail orders (Walk-in or Saved) must be fully paid immediately.");
        }

        // Validate Credit Limit
        if (!isPaid && so.getCustomer().getCreditLimit() != null
                && so.getCustomer().getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentDebt = invoiceRepository.sumOutstandingBalanceByCustomer(so.getCustomer().getId());
            if (currentDebt == null)
                currentDebt = BigDecimal.ZERO;

            BigDecimal newTotalDebt = currentDebt.add(total);
            if (newTotalDebt.compareTo(so.getCustomer().getCreditLimit()) > 0) {
                // Formatting for display
                throw new Exception("Credit Limit Reached! Limit: " + so.getCustomer().getCreditLimit()
                        + ", Current Debt: " + currentDebt
                        + ", This Order: " + total);
            }
        }

        // Create Invoice
        Invoice invoice = new Invoice();
        invoice.setSalesOrder(so);
        invoice.setCustomer(so.getCustomer());
        invoice.setInvoiceDate(LocalDate.now());

        // 1. Safe Numbering Strategy: Save with Temp -> Update with ID
        // Prevents duplicates (Race Condition Fix)
        invoice.setInvoiceNumber("TEMP-" + java.util.UUID.randomUUID());

        invoice.setTotalAmount(total);

        // Determine Status logic
        if (isPaid) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setAmountPaid(total);
            invoice.setBalanceDue(BigDecimal.ZERO);
        } else {
            invoice.setStatus(InvoiceStatus.UNPAID);
            invoice.setAmountPaid(BigDecimal.ZERO);
            invoice.setBalanceDue(total);
        }

        // First Save to generate ID
        invoice = invoiceRepository.save(invoice);

        // Update Number based on ID
        invoice.setInvoiceNumber(String.format("INV-%05d", invoice.getId()));
        invoiceRepository.save(invoice);

        // Create Payment If Paid
        if (isPaid) {
            Payment payment = new Payment();
            payment.setCustomer(so.getCustomer());
            payment.setAmount(total);
            payment.setPaymentDate(LocalDate.now());
            try {
                payment.setPaymentMethod(PaymentMethod.valueOf(paymentMethodStr));
            } catch (Exception e) {
                payment.setPaymentMethod(PaymentMethod.CASH); // Default fallback
            }
            payment.setReferenceNumber("Auto-Payment for " + invoice.getInvoiceNumber());
            paymentRepository.save(payment);
        }

        // Update SO Status
        so.setStatus(SalesOrderStatus.INVOICED);
        orderRepository.save(so);

        // Update Units to SOLD
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            unit.setStatus(InventoryStatus.SOLD);
            unitRepository.save(unit);
        }

        return invoice;
    }

    @Transactional
    public void cancelOrder(Long orderId) throws Exception {
        SalesOrder so = orderRepository.findById(orderId).orElseThrow();

        if (so.getStatus() == SalesOrderStatus.DRAFT) {
            // Simple Clean up for Abandoned / Mistaken Drafts
            auditService.log("CANCEL_DRAFT", "Cancelled Draft Order " + orderId);
            // Revert Units
            for (InventoryUnit unit : so.getAllocatedUnits()) {
                unit.setStatus(InventoryStatus.AVAILABLE);
                unit.setSalesOrder(null);
                unit.setSoldPrice(null);
                unitRepository.save(unit);
            }
            so.getAllocatedUnits().clear();
            so.setStatus(SalesOrderStatus.CANCELLED);
            orderRepository.save(so);
            return;
        }

        if (so.getStatus() != SalesOrderStatus.INVOICED) {
            throw new Exception("Only finalized invoices or Drafts can be cancelled.");
        }

        // Cancel Invoice
        com.mushroom.stockkeeper.model.Invoice invoice = invoiceRepository.findBySalesOrder(so)
                .orElseThrow(() -> new Exception("Invoice not found for this order."));

        auditService.log("CANCEL_ORDER", "Cancelling Order " + orderId + " (Inv: " + invoice.getInvoiceNumber() + ")");

        // SAFE CANCELLATION: Issue Refund if Paid
        // Fix: Deduct any amounts already returned via Credit Notes
        java.util.List<CreditNote> existingNotes = creditNoteRepository.findByOriginalInvoice(invoice);
        BigDecimal alreadyReturned = existingNotes.stream()
                .map(CreditNote::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refundableAmount = invoice.getAmountPaid().subtract(alreadyReturned);
        if (refundableAmount.compareTo(BigDecimal.ZERO) < 0) {
            refundableAmount = BigDecimal.ZERO;
        }

        if (refundableAmount.compareTo(BigDecimal.ZERO) > 0) {
            CreditNote refundNote = new CreditNote();
            refundNote.setCustomer(invoice.getCustomer());
            refundNote.setOriginalInvoice(invoice);
            refundNote.setAmount(refundableAmount); // Only refund net remaining
            refundNote.setNoteDate(LocalDate.now());
            refundNote.setReason("Refund for Cancelled Order " + invoice.getInvoiceNumber());
            refundNote.setNoteNumber("RF-" + System.currentTimeMillis());

            // Auto-Refund logic for Retail
            boolean isRetail = invoice.getCustomer().getType() == CustomerType.RETAIL
                    || invoice.getCustomer().isHidden();

            if (isRetail) {
                refundNote.setUsed(true);
                refundNote.setRemainingAmount(BigDecimal.ZERO);
                refundNote.setReason(refundNote.getReason() + " (Cash Refund)");
            } else {
                refundNote.setUsed(false);
                refundNote.setRemainingAmount(refundableAmount);
            }

            creditNoteRepository.save(refundNote);

            auditService.log("ISSUE_REFUND", "Refund Note " + refundNote.getNoteNumber() + " for Order " + orderId);
        } else if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            auditService.log("CANCEL_INFO",
                    "Order " + orderId + " cancelled but full amount already returned via Credit Notes.");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setBalanceDue(BigDecimal.ZERO);
        // We keep TotalAmount and AmountPaid for historical record, but status is
        // CANCELLED.
        invoiceRepository.save(invoice);

        // Revert Units
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            if (unit.getStatus() == InventoryStatus.RETURNED || unit.getStatus() == InventoryStatus.SPOILED) {
                // Keep state, just unlink (already returned)
                unit.setSalesOrder(null);
                unitRepository.save(unit);
                continue;
            }

            unit.setStatus(InventoryStatus.AVAILABLE);
            unit.setSalesOrder(null); // Unlink from order to make available for others
            unit.setSoldPrice(null);
            unitRepository.save(unit);
        }

        // Clear the list in memory to avoid confusion
        so.getAllocatedUnits().clear();

        // Update Order Status
        so.setStatus(SalesOrderStatus.CANCELLED);
        orderRepository.save(so);
    }

    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * *") // Run every hour
    public void cleanupOldDrafts() {
        // We need a repository method or just iterate.
        // Iterate for now as we don't have createdAt field exposed in spec query easily
        // without updating entity/repo
        // Actually, SalesOrder has OrderDate (LocalDate). Let's use that for "older
        // than 2 days" to be safe.
        LocalDate cutoffDate = LocalDate.now().minusDays(2);

        java.util.List<SalesOrder> drafts = orderRepository.findByStatus(SalesOrderStatus.DRAFT);
        int cleaned = 0;
        for (SalesOrder s : drafts) {
            if (s.getOrderDate().isBefore(cutoffDate)) {
                try {
                    cancelOrder(s.getId()); // Reuses existing logic to free units
                    cleaned++;
                } catch (Exception e) {
                    // Log error but continue
                    System.err.println("Failed to cleanup draft " + s.getId() + ": " + e.getMessage());
                }
            }
        }
        if (cleaned > 0) {
            auditService.log("DATA_CLEANUP", "Cleaned up " + cleaned + " abandoned stale draft orders.");
        }
    }
}
