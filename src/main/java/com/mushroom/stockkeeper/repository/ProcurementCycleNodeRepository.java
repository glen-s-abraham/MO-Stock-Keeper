package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.ProcurementCycleNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcurementCycleNodeRepository extends JpaRepository<ProcurementCycleNode, Long> {

    List<ProcurementCycleNode> findByScheduleIdOrderByCycleIndexAsc(Long scheduleId);

    Optional<ProcurementCycleNode> findByScheduleIdAndCycleIndex(Long scheduleId, Integer cycleIndex);
}
