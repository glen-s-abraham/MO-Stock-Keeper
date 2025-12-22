package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Invoice;
import com.mushroom.stockkeeper.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByStatusNot(InvoiceStatus status); // For finding unpaid/partial

    List<Invoice> findByCustomerIdAndStatusNot(Long customerId, InvoiceStatus status);

    Optional<Invoice> findBySalesOrder(com.mushroom.stockkeeper.model.SalesOrder salesOrder);

    Optional<Invoice> findTopByOrderByIdDesc();

    Optional<Invoice> findTopByCustomerIdOrderByInvoiceDateDesc(Long customerId);

    java.util.Optional<List<Invoice>> findByCustomerIdOrderByInvoiceDateDesc(Long customerId);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(i.balanceDue) FROM Invoice i WHERE i.customer.id = :customerId AND i.status != 'CANCELLED'")
    java.math.BigDecimal sumOutstandingBalanceByCustomer(Long customerId);

    @org.springframework.data.jpa.repository.Query("SELECT i.customer.id, SUM(i.balanceDue), COUNT(i) FROM Invoice i WHERE i.customer.type = 'WHOLESALE' AND i.status != 'CANCELLED' AND i.status != 'PAID' GROUP BY i.customer.id")
    List<Object[]> findWholesaleOutstandingBalances(); // Returns [customerId, totalDue, countUnpaid]

    @org.springframework.data.jpa.repository.Query("SELECT i.customer.id, i.status, COUNT(i) FROM Invoice i WHERE i.customer.type = 'WHOLESALE' GROUP BY i.customer.id, i.status")
    List<Object[]> findWholesaleInvoiceCounts();

    List<Invoice> findTop20BySalesOrderOrderTypeOrderByInvoiceDateDesc(String orderType);
}
