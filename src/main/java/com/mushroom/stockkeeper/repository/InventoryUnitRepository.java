package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.InventoryUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, Long> {
    Optional<InventoryUnit> findByUuid(String uuid);

    java.util.List<InventoryUnit> findByStatus(com.mushroom.stockkeeper.model.InventoryStatus status);

    long countByBatchIdAndStatusNot(Long batchId, com.mushroom.stockkeeper.model.InventoryStatus status);

    void deleteByBatchId(Long batchId);

    java.util.List<InventoryUnit> findByBatchId(Long batchId);

    long countBySalesOrderCustomerId(Long customerId);

    long countBySalesOrderCustomerIdAndStatus(Long customerId, com.mushroom.stockkeeper.model.InventoryStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM InventoryUnit u WHERE u.uuid = :uuid")
    Optional<InventoryUnit> findByUuidForUpdate(
            @org.springframework.web.bind.annotation.RequestParam("uuid") String uuid);
}
