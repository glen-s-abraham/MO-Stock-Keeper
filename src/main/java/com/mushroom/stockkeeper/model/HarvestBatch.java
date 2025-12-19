package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "harvest_batches")
@Data
public class HarvestBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String batchCode; // e.g. B-20231027-001

    @Column(nullable = false)
    private LocalDate batchDate;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer expiryDays;

    private LocalDate expiryDate;

    private Integer totalUnits;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (batchDate == null) {
            batchDate = LocalDate.now();
        }
        if (expiryDays != null && expiryDate == null) {
            expiryDate = batchDate.plusDays(expiryDays);
        }
    }
}
