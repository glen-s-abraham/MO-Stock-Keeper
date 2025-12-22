package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Optional<CreditNote> findByNoteNumber(String noteNumber);

    Optional<CreditNote> findByGeneratedFromPayment(com.mushroom.stockkeeper.model.Payment payment);
}
