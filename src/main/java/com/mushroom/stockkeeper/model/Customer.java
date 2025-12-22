package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "customers")
@Data
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String phoneNumber;
    private String email;
    private String address;

    private boolean isHidden = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType type = CustomerType.WHOLESALE;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal creditLimit; // Max allowed outstanding balance
}
