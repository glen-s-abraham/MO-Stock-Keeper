package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private SalesOrderRepository orderRepository;
    @Mock
    private InventoryUnitRepository unitRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CreditNoteRepository creditNoteRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SalesService salesService;

    private Customer retailCustomer;
    private Customer wholesaleCustomer;
    private Product product;
    private HarvestBatch batch;
    private InventoryUnit unit;

    @BeforeEach
    void setUp() {
        // Setup Common Data
        retailCustomer = new Customer();
        retailCustomer.setId(1L);
        retailCustomer.setName("John Doe");
        retailCustomer.setType(CustomerType.RETAIL);

        wholesaleCustomer = new Customer();
        wholesaleCustomer.setId(2L);
        wholesaleCustomer.setName("Big Grocery");
        wholesaleCustomer.setType(CustomerType.WHOLESALE);
        wholesaleCustomer.setCreditLimit(new BigDecimal("1000.00"));

        product = new Product();
        product.setId(100L);
        product.setName("Button Mushrooms");
        product.setRetailPrice(new BigDecimal("10.00"));
        product.setWholesalePrice(new BigDecimal("8.00"));

        batch = new HarvestBatch();
        batch.setId(50L);
        batch.setProduct(product);

        unit = new InventoryUnit();
        unit.setId(500L);
        unit.setUuid("U:123");
        unit.setBatch(batch);
        unit.setStatus(InventoryStatus.AVAILABLE);
    }

    @Test
    void createOrder_ShouldReturnDraftOrder() {
        when(orderRepository.save(any(SalesOrder.class))).thenAnswer(i -> i.getArguments()[0]);

        SalesOrder order = salesService.createOrder(retailCustomer, "RETAIL", "CASH");

        assertNotNull(order);
        assertEquals(SalesOrderStatus.DRAFT, order.getStatus());
        assertEquals(retailCustomer, order.getCustomer());
        assertEquals("RETAIL", order.getOrderType());
    }

    @Test
    void allocateUnit_ShouldLinkUnitAndSetPrice_Retail() throws Exception {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderType("RETAIL");
        order.setStatus(SalesOrderStatus.DRAFT);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(unitRepository.findByUuidForUpdate("123")).thenReturn(Optional.of(unit));

        salesService.allocateUnit(1L, "U:123");

        assertEquals(InventoryStatus.ALLOCATED, unit.getStatus());
        assertEquals(order, unit.getSalesOrder());
        // Should use Retail Price
        assertEquals(new BigDecimal("10.00"), unit.getSoldPrice());
        verify(unitRepository, times(1)).save(unit);
    }

    @Test
    void allocateUnit_ShouldLinkUnitAndSetPrice_Wholesale() throws Exception {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderType("WHOLESALE");
        order.setStatus(SalesOrderStatus.DRAFT);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(unitRepository.findByUuidForUpdate("123")).thenReturn(Optional.of(unit));

        salesService.allocateUnit(1L, "U:123");

        // Should use Wholesale Price
        assertEquals(new BigDecimal("8.00"), unit.getSoldPrice());
    }

    @Test
    void allocateUnit_ShouldFail_IfUnitNotAvailable() {
        unit.setStatus(InventoryStatus.SOLD);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(new SalesOrder()));
        when(unitRepository.findByUuidForUpdate("123")).thenReturn(Optional.of(unit));

        Exception exception = assertThrows(Exception.class, () -> {
            salesService.allocateUnit(1L, "U:123");
        });

        assertTrue(exception.getMessage().contains("not AVAILABLE"));
    }

    @Test
    void finalizeOrder_ShouldCreateInvoice_WhenPaid() throws Exception {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setCustomer(retailCustomer);
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setAllocatedUnits(new ArrayList<>());

        // Add a unit
        unit.setSoldPrice(new BigDecimal("10.00"));
        unit.setSalesOrder(order);
        order.getAllocatedUnits().add(unit);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = (Invoice) i.getArguments()[0];
            inv.setId(99L);
            return inv;
        });

        Invoice invoice = salesService.finalizeOrder(1L, true, "CASH");

        assertNotNull(invoice);
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(new BigDecimal("10.00"), invoice.getTotalAmount());
        assertEquals(new BigDecimal("10.00"), invoice.getAmountPaid());

        // Verify Unit updated to SOLD
        assertEquals(InventoryStatus.SOLD, unit.getStatus());
        // Verify Order updated
        assertEquals(SalesOrderStatus.INVOICED, order.getStatus());

        // Verify Payment Created
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void finalizeOrder_ShouldEnforceCreditLimit() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setCustomer(wholesaleCustomer); // Limit 1000
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setAllocatedUnits(new ArrayList<>());

        // Add expensive units (Total 1200)
        unit.setSoldPrice(new BigDecimal("1200.00"));
        order.getAllocatedUnits().add(unit);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(invoiceRepository.sumOutstandingBalanceByCustomer(wholesaleCustomer.getId())).thenReturn(BigDecimal.ZERO);

        Exception exception = assertThrows(Exception.class, () -> {
            salesService.finalizeOrder(1L, false, "CREDIT");
        });

        assertTrue(exception.getMessage().contains("Credit Limit Reached"));
    }
}
