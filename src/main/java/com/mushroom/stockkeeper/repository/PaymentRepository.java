package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCustomerId(Long customerId);

    List<Payment> findTop3ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
