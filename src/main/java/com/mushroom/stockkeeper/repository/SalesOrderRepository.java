package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.SalesOrder;
import com.mushroom.stockkeeper.model.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Optional<SalesOrder> findByOrderNumber(String orderNumber);

    List<SalesOrder> findByStatus(SalesOrderStatus status);

    long countByCustomerId(Long customerId);

    List<SalesOrder> findTop20ByOrderTypeOrderByCreatedAtDesc(String orderType);
}
