package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class SalesService {

    private final SalesOrderRepository orderRepository;
    private final InventoryUnitRepository unitRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public SalesService(SalesOrderRepository orderRepository, InventoryUnitRepository unitRepository,
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public SalesOrder createOrder(Customer customer) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setOrderDate(LocalDate.now());
        so.setStatus(SalesOrderStatus.DRAFT);
        // SO Number: SO-Time
        so.setOrderNumber("SO-" + System.currentTimeMillis());
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

        InventoryUnit unit = unitRepository.findByUuid(uuid)
                .orElseThrow(() -> new Exception("Unit not found: " + uuid));

        if (unit.getStatus() != InventoryStatus.AVAILABLE) {
            throw new Exception("Unit " + uuid + " is not AVAILABLE (Status: " + unit.getStatus() + ")");
        }

        // Link
        unit.setSalesOrder(so);
        unit.setStatus(InventoryStatus.ALLOCATED);
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
        SalesOrder so = orderRepository.findById(orderId).orElseThrow();

        if (so.getStatus() == SalesOrderStatus.INVOICED || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new Exception("Cannot modify finalized order.");
        }

        for (InventoryUnit unit : so.getAllocatedUnits()) {
            if (unit.getBatch().getProduct().getId().equals(productId)) {
                unit.setSoldPrice(price);
                unitRepository.save(unit);
            }
        }
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

        // Validate Validation for Hidden/Guest Customers
        if (so.getCustomer().isHidden() && !isPaid) {
            throw new Exception("Walk-in or Guest orders must be fully paid (Prepaid only).");
        }

        // Create Invoice
        Invoice invoice = new Invoice();
        invoice.setSalesOrder(so);
        invoice.setCustomer(so.getCustomer());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());

        // Calculate Total
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            total = total.add(unit.getSoldPrice());
        }

        invoice.setTotalAmount(total);

        if (isPaid) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setAmountPaid(total);
            invoice.setBalanceDue(BigDecimal.ZERO);

            // Create Payment Record
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

        } else {
            invoice.setStatus(InvoiceStatus.UNPAID);
            invoice.setAmountPaid(BigDecimal.ZERO);
            invoice.setBalanceDue(total);
        }

        invoiceRepository.save(invoice);

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

        if (so.getStatus() != SalesOrderStatus.INVOICED) {
            throw new Exception("Only finalized invoices can be cancelled.");
        }

        // Cancel Invoice
        com.mushroom.stockkeeper.model.Invoice invoice = invoiceRepository.findBySalesOrder(so)
                .orElseThrow(() -> new Exception("Invoice not found for this order."));
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setBalanceDue(BigDecimal.ZERO);
        invoice.setTotalAmount(BigDecimal.ZERO);
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoiceRepository.save(invoice);

        // Revert Units
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            unit.setStatus(InventoryStatus.AVAILABLE);
            unit.setSalesOrder(null); // Unlink from order to make available for others
            unit.setSoldPrice(null);
            unitRepository.save(unit);
        }

        // Clear the list in memory to avoid confusion if object is reused in
        // transaction (optional but safe)
        so.getAllocatedUnits().clear();

        // Update Order Status
        so.setStatus(SalesOrderStatus.CANCELLED);
        orderRepository.save(so);
    }
}
