package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_units")
@Data
public class InventoryUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uuid; // Unique Serial Number

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    private HarvestBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryStatus status;

    @ManyToOne
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Column(nullable = false, length = 1000)
    private String qrCodeContent;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal soldPrice;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null)
            status = InventoryStatus.AVAILABLE;
    }
}
