package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
