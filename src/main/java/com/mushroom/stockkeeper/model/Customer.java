package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sk_customers")
@Data
@org.hibernate.annotations.SQLDelete(sql = "UPDATE sk_customers SET deleted = true WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("deleted = false")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Customer Name is required")
    @jakarta.validation.constraints.Size(min = 2, message = "Name must be at least 2 characters")
    private String name;

    private String phone;
    private String email;
    private String address;
    private String contactPerson;
    private String tin;

    private boolean isHidden = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType type = CustomerType.WHOLESALE;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal creditLimit; // Max allowed outstanding balance

    private boolean deleted = false;
}
