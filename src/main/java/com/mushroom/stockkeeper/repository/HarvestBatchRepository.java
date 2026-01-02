package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.HarvestBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HarvestBatchRepository extends JpaRepository<HarvestBatch, Long>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<HarvestBatch> {
        Optional<HarvestBatch> findByBatchCode(String batchCode);

        long countByProductId(Long productId);

        java.util.List<HarvestBatch> findByExpiryDateGreaterThanEqualOrExpiryDateIsNull(java.time.LocalDate date);

        java.util.List<HarvestBatch> findByExpiryDateLessThan(java.time.LocalDate date);

        // Filter by Product
        java.util.List<HarvestBatch> findByProductId(Long productId);

        @org.springframework.data.jpa.repository.Query("SELECT b FROM HarvestBatch b WHERE b.product.id = :productId AND (b.expiryDate >= :date OR b.expiryDate IS NULL)")
        java.util.List<HarvestBatch> findActiveByProduct(
                        @org.springframework.data.repository.query.Param("productId") Long productId,
                        @org.springframework.data.repository.query.Param("date") java.time.LocalDate date);

        @org.springframework.data.jpa.repository.Query("SELECT b FROM HarvestBatch b WHERE b.product.id = :productId AND b.expiryDate < :date")
        java.util.List<HarvestBatch> findExpiredByProduct(
                        @org.springframework.data.repository.query.Param("productId") Long productId,
                        @org.springframework.data.repository.query.Param("date") java.time.LocalDate date);

        @org.springframework.data.jpa.repository.Query("SELECT b.product.id, COUNT(b) FROM HarvestBatch b WHERE b.expiryDate >= :date OR b.expiryDate IS NULL GROUP BY b.product.id")
        java.util.List<Object[]> countActiveBatchesGrouped(
                        @org.springframework.data.repository.query.Param("date") java.time.LocalDate date);

        @org.springframework.data.jpa.repository.Query("SELECT b.product.id, COUNT(b) FROM HarvestBatch b WHERE b.expiryDate < :date GROUP BY b.product.id")
        java.util.List<Object[]> countExpiredBatchesGrouped(
                        @org.springframework.data.repository.query.Param("date") java.time.LocalDate date);
}
