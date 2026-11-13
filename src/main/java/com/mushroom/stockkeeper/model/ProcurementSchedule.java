package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sk_procurement_schedules")
@Data
public class ProcurementSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Schedule name is required")
    @Column(nullable = false, length = 150)
    private String scheduleName;

    @NotNull
    @Min(value = 7, message = "Supply lasting days must be at least 7")
    @Column(nullable = false)
    private Integer supplyLastingDays;

    @NotNull
    @Min(value = 1, message = "Vendor lead time must be at least 1 day")
    @Column(nullable = false)
    private Integer vendorLeadTime;

    @NotNull
    @Min(value = 0, message = "Action buffer cannot be negative")
    @Column(nullable = false)
    private Integer actionBuffer;

    @NotNull
    @Min(value = 0, message = "Preferred delivery day must be between 0 (Sunday) and 6 (Saturday)")
    @Max(value = 6, message = "Preferred delivery day must be between 0 (Sunday) and 6 (Saturday)")
    @Column(nullable = false)
    private Integer preferredDeliveryDay;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Long version;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("cycleIndex ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ProcurementCycleNode> nodes = new ArrayList<>();

    public String getPreferredDeliveryDayName() {
        if (preferredDeliveryDay == null) {
            return "";
        }
        return switch (preferredDeliveryDay) {
            case 0 -> "Sunday";
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            default -> "Unknown";
        };
    }
}
