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
}
