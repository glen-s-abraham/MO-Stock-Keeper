package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(name = "sk_procurement_cycle_nodes")
@Data
public class ProcurementCycleNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProcurementSchedule schedule;

    @Column(nullable = false)
    private Integer cycleIndex;

    @Column(nullable = false)
    private LocalDate theoreticalNeedDate;

    @Column(nullable = false)
    private LocalDate actualDeliveryDate;

    @Column(nullable = false)
    private LocalDate orderCutoffDate;

    @Column(nullable = false)
    private LocalDate unadjustedActionDate;

    @Column(nullable = false)
    private LocalDate actionDate;

    @Column(nullable = false)
    private boolean manualOverride = false;

    @Column(nullable = false)
    private Integer gapDays = 0;

    @Column(nullable = false)
    private boolean stockoutRisk = false;

    @Column(nullable = false)
    private boolean holidayAdjusted = false;

    @Column(nullable = false)
    private Integer holidayAdjustedDays = 0;

    public String getStockoutWarningMessage() {
        if (!stockoutRisk || schedule == null) {
            return null;
        }
        return String.format(
                "Stockout Risk: Gap is %d days, supply only lasts %d. Increase order volume or pull delivery forward.",
                gapDays, schedule.getSupplyLastingDays());
    }
}
