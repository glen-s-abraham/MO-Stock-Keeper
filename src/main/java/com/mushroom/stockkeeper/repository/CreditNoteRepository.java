package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Optional<CreditNote> findByNoteNumber(String noteNumber);

    java.util.Optional<CreditNote> findByGeneratedFromPayment(com.mushroom.stockkeeper.model.Payment payment);

    java.util.List<CreditNote> findByOriginalInvoice(com.mushroom.stockkeeper.model.Invoice originalInvoice);

    java.util.List<CreditNote> findByCustomerIdAndRemainingAmountGreaterThanOrderByNoteDateAsc(Long customerId,
            java.math.BigDecimal amount);

    @org.springframework.data.jpa.repository.Query("SELECT c.customer.id, SUM(c.remainingAmount) FROM CreditNote c WHERE c.customer.type = 'WHOLESALE' AND c.remainingAmount > 0 GROUP BY c.customer.id")
    java.util.List<Object[]> findWholesaleRemainingCredits();

    long countByCustomerId(Long customerId);

    java.util.List<CreditNote> findByCustomerId(Long customerId);
}
