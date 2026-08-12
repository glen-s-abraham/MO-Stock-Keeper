package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "sk_nutrition_line_items")
@Data
@org.hibernate.annotations.SQLDelete(sql = "UPDATE sk_nutrition_line_items SET deleted = true WHERE id = ? and version = ?")
@org.hibernate.annotations.SQLRestriction("deleted = false")
public class NutritionLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Component name is required")
    private String componentName;

    @Column(nullable = false, precision = 10, scale = 2)
    @jakarta.validation.constraints.Min(value = 0, message = "Amount cannot be negative")
    private BigDecimal amount;

    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Measurement unit is required")
    private String measurementUnit;

    private Integer displayOrder = 0;

    private boolean deleted = false;
}
