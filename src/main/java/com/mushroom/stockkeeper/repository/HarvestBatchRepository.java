package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.HarvestBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HarvestBatchRepository extends JpaRepository<HarvestBatch, Long> {
    Optional<HarvestBatch> findByBatchCode(String batchCode);
}
