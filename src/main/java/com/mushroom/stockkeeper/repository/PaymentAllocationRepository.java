package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Payment;
import com.mushroom.stockkeeper.model.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByPayment(Payment payment);
}
