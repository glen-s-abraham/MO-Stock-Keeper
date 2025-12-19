package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
@Data
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesOrderStatus status;

    // In a simpler model for this MVP, we can link Units directly to SO.
    // Or we have LineItems (Product + Qty + Price) and then Units are linked to
    // LineItems.
    // For traceability, linking Unit to SO is crucial.

    @OneToMany(mappedBy = "salesOrder") // We need to add 'salesOrder' to InventoryUnit
    private List<InventoryUnit> allocatedUnits = new ArrayList<>();

    // We also need pricing. Usually pricing is per Product.
    // Pricing is now handled in InventoryUnit.soldPrice to allow multi-product
    // orders with different prices.

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (orderDate == null)
            orderDate = LocalDate.now();
        if (status == null)
            status = SalesOrderStatus.DRAFT;
    }
}
