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
    @jakarta.validation.constraints.NotBlank(message = "Product Name is required")
    @jakarta.validation.constraints.Size(min = 2, message = "Name must be at least 2 characters")
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

    // Pricing
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal retailPrice;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal wholesalePrice;

    private boolean deleted = false;
}
