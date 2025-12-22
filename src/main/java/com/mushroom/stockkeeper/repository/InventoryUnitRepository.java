package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.InventoryUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, Long> {
    Optional<InventoryUnit> findByUuid(String uuid);

    java.util.List<InventoryUnit> findByStatus(com.mushroom.stockkeeper.model.InventoryStatus status);

    long countByBatchIdAndStatusNot(Long batchId, com.mushroom.stockkeeper.model.InventoryStatus status);

    void deleteByBatchId(Long batchId);
}
