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

    public SalesService(SalesOrderRepository orderRepository, InventoryUnitRepository unitRepository,
            InvoiceRepository invoiceRepository) {
        this.orderRepository = orderRepository;
        this.unitRepository = unitRepository;
        this.invoiceRepository = invoiceRepository;
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
    public void removeUnit(Long unitId) {
        InventoryUnit unit = unitRepository.findById(unitId).orElseThrow();
        if (unit.getStatus() == InventoryStatus.ALLOCATED) {
            unit.setSalesOrder(null);
            unit.setStatus(InventoryStatus.AVAILABLE);
            unitRepository.save(unit);
        }
    }

    @Transactional
    public void updateProductPrice(Long orderId, Long productId, BigDecimal price) {
        SalesOrder so = orderRepository.findById(orderId).orElseThrow();
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            if (unit.getBatch().getProduct().getId().equals(productId)) {
                unit.setSoldPrice(price);
                unitRepository.save(unit);
            }
        }
    }

    @Transactional
    public Invoice finalizeOrder(Long orderId) throws Exception {
        SalesOrder so = orderRepository.findById(orderId).orElseThrow();
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

        // Create Invoice
        Invoice invoice = new Invoice();
        invoice.setSalesOrder(so);
        invoice.setCustomer(so.getCustomer());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());

        // Calculate Total
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryUnit unit : so.getAllocatedUnits()) {
            total = total.add(unit.getSoldPrice());
        }

        invoice.setTotalAmount(total);
        invoice.setBalanceDue(total);
        invoice.setAmountPaid(BigDecimal.ZERO);

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
}
