package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "products")
@Data
@org.hibernate.annotations.SQLDelete(sql = "UPDATE products SET deleted = true WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("deleted = false")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String sku;

    @ManyToOne
    @JoinColumn(name = "uom_id", nullable = false)
    private UOM uom;

    private String description;

    @Column(length = 500)
    private String storageInstructions;

    // Default shelf life in days
    private Integer defaultExpiryDays = 7;

    private boolean deleted = false;
}
