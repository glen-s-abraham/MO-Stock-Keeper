package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sk_uom")
@Data
public class UOM {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // e.g. KG, BOX, PACK

    private String description;
}
