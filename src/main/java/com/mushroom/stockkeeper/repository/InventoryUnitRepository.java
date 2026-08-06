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

    @org.springframework.data.jpa.repository.Query("SELECT new com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto(p.name, COUNT(u)) " +
            "FROM InventoryUnit u JOIN u.batch b JOIN b.product p " +
            "GROUP BY p.name")
    java.util.List<com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto> getLifetimeHarvestTotals();

    @org.springframework.data.jpa.repository.Query("SELECT new com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto(p.name, COUNT(u)) " +
            "FROM InventoryUnit u JOIN u.batch b JOIN b.product p " +
            "WHERE b.batchDate >= :startDate AND b.batchDate < :endDate " +
            "GROUP BY p.name")
    java.util.List<com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto> getPeriodicHarvestTotals(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);

    @org.springframework.data.jpa.repository.Query("SELECT new com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto(p.name, CAST(EXTRACT(DAY FROM b.batchDate) AS integer), COUNT(u)) " +
            "FROM InventoryUnit u JOIN u.batch b JOIN b.product p " +
            "WHERE b.batchDate >= :startDate AND b.batchDate < :endDate " +
            "GROUP BY p.name, EXTRACT(DAY FROM b.batchDate)")
    java.util.List<com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto> getDailyHarvestTotals(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);

    @org.springframework.data.jpa.repository.Query("SELECT new com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto(p.name, CAST(EXTRACT(WEEK FROM b.batchDate) AS integer), COUNT(u)) " +
            "FROM InventoryUnit u JOIN u.batch b JOIN b.product p " +
            "WHERE b.batchDate >= :startDate AND b.batchDate < :endDate " +
            "GROUP BY p.name, EXTRACT(WEEK FROM b.batchDate)")
    java.util.List<com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto> getWeeklyHarvestTotals(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);

    @org.springframework.data.jpa.repository.Query("SELECT new com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto(p.name, CAST(EXTRACT(MONTH FROM b.batchDate) AS integer), COUNT(u)) " +
            "FROM InventoryUnit u JOIN u.batch b JOIN b.product p " +
            "WHERE b.batchDate >= :startDate AND b.batchDate < :endDate " +
            "GROUP BY p.name, EXTRACT(MONTH FROM b.batchDate)")
    java.util.List<com.mushroom.stockkeeper.dto.TimeSeriesAggregateDto> getMonthlyDistributionTotals(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);
}
