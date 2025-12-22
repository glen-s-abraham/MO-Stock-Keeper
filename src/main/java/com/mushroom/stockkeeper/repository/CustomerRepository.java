package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    java.util.List<Customer> findByIsHiddenFalse();

    java.util.List<Customer> findByType(com.mushroom.stockkeeper.model.CustomerType type);

    java.util.List<Customer> findByTypeAndIsHiddenFalse(com.mushroom.stockkeeper.model.CustomerType type);

    java.util.Optional<Customer> findByName(String name);
}
