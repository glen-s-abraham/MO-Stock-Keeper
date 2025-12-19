package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "app_settings")
@Data
public class AppSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String settingKey;

    @Column(nullable = false)
    private String settingValue;

    public AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.settingKey = key;
        this.settingValue = value;
    }
}
