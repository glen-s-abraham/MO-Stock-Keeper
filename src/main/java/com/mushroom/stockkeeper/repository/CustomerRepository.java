package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Customer> {
    java.util.List<Customer> findByIsHiddenFalse();

    java.util.List<Customer> findByType(com.mushroom.stockkeeper.model.CustomerType type);

    java.util.List<Customer> findByTypeAndIsHiddenFalse(com.mushroom.stockkeeper.model.CustomerType type);

    java.util.Optional<Customer> findByName(String name);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c WHERE c.id = :id")
    java.util.Optional<Customer> findByIdForUpdate(@org.springframework.web.bind.annotation.RequestParam("id") Long id);
}
