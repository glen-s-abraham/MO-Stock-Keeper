package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Optional<CreditNote> findByNoteNumber(String noteNumber);

    Optional<CreditNote> findByGeneratedFromPayment(com.mushroom.stockkeeper.model.Payment payment);

    @org.springframework.data.jpa.repository.Query("SELECT c.customer.id, SUM(c.remainingAmount) FROM CreditNote c WHERE c.customer.type = 'WHOLESALE' AND c.remainingAmount > 0 GROUP BY c.customer.id")
    java.util.List<Object[]> findWholesaleRemainingCredits();
}
