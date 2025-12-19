package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
