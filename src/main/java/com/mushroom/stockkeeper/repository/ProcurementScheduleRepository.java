package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.ProcurementSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcurementScheduleRepository extends JpaRepository<ProcurementSchedule, Long> {

    List<ProcurementSchedule> findByActiveTrueOrderByCreatedAtDesc();

    Optional<ProcurementSchedule> findByIdAndActiveTrue(Long id);
}
