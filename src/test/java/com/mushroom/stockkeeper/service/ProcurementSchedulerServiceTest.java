package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.ProcurementCycleNode;
import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.repository.ProcurementScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementSchedulerServiceTest {

    @Mock
    private ProcurementScheduleRepository scheduleRepository;

    @Mock
    private SettingsService settingsService;

    private ProcurementSchedulerService schedulerService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Fixed date: Friday, 2026-11-13
        fixedClock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneId.of("UTC"));
        schedulerService = new ProcurementSchedulerService(scheduleRepository, settingsService, fixedClock);
        lenient().when(settingsService.getProcurementBlackoutDates()).thenReturn(Collections.emptySet());
        lenient().when(scheduleRepository.save(any(ProcurementSchedule.class)))
                .thenAnswer(invocation -> {
                    ProcurementSchedule s = invocation.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(1L);
                    }
                    return s;
                });
    }

    @Test
    void createSchedule_ShouldProject12MonthsWithBackwardSnappingAndCutoffAndActionDates() {
        // Example from spec:
        // Schedule_Name = "Raw Substrate - 175kg", Supply_Lasting_Days = 28,
        // Vendor_Lead_Time = 15, Action_Buffer = 3, Preferred_Delivery_Day = 6 (Saturday)
        // Initial delivery date: Saturday 2026-12-05
        LocalDate initialDelivery = LocalDate.of(2026, 12, 5);

        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Raw Substrate - 175kg", 28, 15, 3, 6, initialDelivery);

        assertNotNull(schedule);
        assertEquals("Raw Substrate - 175kg", schedule.getScheduleName());
        assertEquals(12, schedule.getNodes().size());

        // Month 1 (n=1)
        ProcurementCycleNode m1 = schedule.getNodes().get(0);
        assertEquals(1, m1.getCycleIndex());
        assertEquals(LocalDate.of(2026, 12, 5), m1.getActualDeliveryDate());
        assertEquals(DayOfWeek.SATURDAY, m1.getActualDeliveryDate().getDayOfWeek());
        assertEquals(LocalDate.of(2026, 11, 20), m1.getOrderCutoffDate()); // Dec 5 - 15d
        assertEquals(LocalDate.of(2026, 11, 17), m1.getActionDate());      // Nov 20 - 3d

        // Check all 12 nodes follow the cascading algorithm
        for (int i = 1; i < 12; i++) {
            ProcurementCycleNode prev = schedule.getNodes().get(i - 1);
            ProcurementCycleNode curr = schedule.getNodes().get(i);
            assertEquals(i + 1, curr.getCycleIndex());
            assertEquals(prev.getActualDeliveryDate().plusDays(28), curr.getTheoreticalNeedDate());
            assertEquals(DayOfWeek.SATURDAY, curr.getActualDeliveryDate().getDayOfWeek());
            assertFalse(curr.getActualDeliveryDate().isAfter(curr.getTheoreticalNeedDate()),
                    "Must snap backwards (on or before Theoretical_Need_Date)");
            assertEquals(curr.getActualDeliveryDate().minusDays(15), curr.getOrderCutoffDate());
            assertEquals(curr.getOrderCutoffDate().minusDays(3), curr.getActionDate());
        }
    }

    @Test
    void snapBackwardToPreferredDay_ShouldSnapBackwardsNotForwards() {
        // Suppose Theoretical_Need_Date is Tuesday 2026-12-08 and Preferred_Delivery_Day is Saturday (6)
        LocalDate theoreticalNeed = LocalDate.of(2026, 12, 8); // Tuesday
        LocalDate snapped = ProcurementSchedulerService.snapBackwardToPreferredDay(theoreticalNeed, 6);

        // Previous Saturday is 2026-12-05 (not 2026-12-12!)
        assertEquals(LocalDate.of(2026, 12, 5), snapped);
        assertEquals(DayOfWeek.SATURDAY, snapped.getDayOfWeek());
    }

    @Test
    void overrideDeliveryDate_ShouldCascadeMonths2Through12AndReturnToastMessage() {
        LocalDate initialDelivery = LocalDate.of(2026, 12, 5); // Saturday
        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Raw Substrate - 175kg", 28, 15, 3, 6, initialDelivery);
        when(scheduleRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(schedule));

        // User shifts Month 1 delivery date by +4 days to Wednesday 2026-12-09
        LocalDate overriddenM1 = LocalDate.of(2026, 12, 9);
        ProcurementSchedulerService.OverrideResult result =
                schedulerService.overrideDeliveryDate(1L, 1, overriddenM1);

        assertEquals(4L, result.shiftedDays());
        assertEquals("Delivery shifted by 4 days. Subsequent 12 months successfully recalculated.",
                result.toastMessage());

        List<ProcurementCycleNode> nodes = result.schedule().getNodes();
        // Month 1 is now 2026-12-09
        assertEquals(overriddenM1, nodes.get(0).getActualDeliveryDate());
        assertTrue(nodes.get(0).isManualOverride());

        // Month 2: Theoretical_Need_Date(2) = 2026-12-09 + 28 = 2027-01-06 (Wednesday)
        // Snaps backward to Saturday 2027-01-02!
        ProcurementCycleNode m2 = nodes.get(1);
        assertEquals(LocalDate.of(2027, 1, 6), m2.getTheoreticalNeedDate());
        assertEquals(LocalDate.of(2027, 1, 2), m2.getActualDeliveryDate());
        assertEquals(DayOfWeek.SATURDAY, m2.getActualDeliveryDate().getDayOfWeek());
        assertEquals(LocalDate.of(2026, 12, 18), m2.getOrderCutoffDate());
        assertEquals(LocalDate.of(2026, 12, 15), m2.getActionDate());
    }

    @Test
    void overrideDeliveryDate_WhenGapExceedsSupplyLastingDays_ShouldFlagStockoutRiskInOrange() {
        LocalDate initialDelivery = LocalDate.of(2026, 12, 5); // Saturday
        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Raw Substrate - 175kg", 28, 15, 3, 6, initialDelivery);
        when(scheduleRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(schedule));

        // Override Month 2 delivery date to 2027-01-04 (30 days after 2026-12-05, while supply lasts 28 days)
        LocalDate m2Override = LocalDate.of(2027, 1, 4);
        ProcurementSchedulerService.OverrideResult result =
                schedulerService.overrideDeliveryDate(1L, 2, m2Override);

        ProcurementCycleNode m2 = result.schedule().getNodes().get(1);
        assertEquals(30, m2.getGapDays());
        assertTrue(m2.isStockoutRisk());
        assertEquals(
                "Stockout Risk: Gap is 30 days, supply only lasts 28. Increase order volume or pull delivery forward.",
                m2.getStockoutWarningMessage());
    }

    @Test
    void month1DeliveryDate_ShouldAllowAnyReceivedOrAnticipatedDateWithinLeadTime_AndCascadeMonths2Through12() {
        // Today is 2026-11-13, Vendor_Lead_Time is 15 (earliest lead-time date would be 2026-11-28)
        // User sets Month 1 delivery date to 2026-11-21 (21st - when goods were received or anticipated)
        LocalDate nov21 = LocalDate.of(2026, 11, 21);

        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Raw Substrate - 175kg", 28, 15, 3, 6, nov21);
        when(scheduleRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(schedule));

        assertEquals(nov21, schedule.getNodes().get(0).getActualDeliveryDate());
        // Month 2: 2026-11-21 + 28 days = 2026-12-19 (Saturday)
        assertEquals(LocalDate.of(2026, 12, 19), schedule.getNodes().get(1).getActualDeliveryDate());

        // Also overriding Month 1 to another date inside lead time (e.g., 2026-11-18 or 2026-11-21) must succeed
        ProcurementSchedulerService.OverrideResult overrideM1 =
                schedulerService.overrideDeliveryDate(1L, 1, LocalDate.of(2026, 11, 22));
        assertEquals(LocalDate.of(2026, 11, 22), overrideM1.schedule().getNodes().get(0).getActualDeliveryDate());
    }

    @Test
    void overrideDeliveryDate_OnSubsequentCycle_WhenViolatingVendorLeadTime_ShouldThrowLeadTimeCollisionError() {
        LocalDate initialDelivery = LocalDate.of(2026, 11, 15);
        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Raw Substrate - 175kg", 28, 15, 3, 6, initialDelivery);
        when(scheduleRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(schedule));

        // Today is 2026-11-13, Vendor_Lead_Time is 15 -> Earliest possible delivery for Month 2+ is 2026-11-28
        // Attempting to set Month 2 delivery to 2026-11-20 must be blocked
        LocalDate invalidTooSoon = LocalDate.of(2026, 11, 20);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schedulerService.overrideDeliveryDate(1L, 2, invalidTooSoon));

        assertEquals(
                "Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is 2026-11-28.",
                ex.getMessage());
    }

    @Test
    void holidayBlackouts_WhenActionDateFallsOnBlackout_ShouldSubtractDaysBeforeHoliday() {
        // Suppose initial delivery is 2027-01-12, Lead_Time = 15 -> Cutoff = 2026-12-28
        // Action_Buffer = 3 -> Unadjusted Action_Date = 2026-12-25 (Christmas)
        // With 2026-12-25 and 2026-12-24 configured as blackout dates, Action_Date shifts back 2 days to 2026-12-23
        when(settingsService.getProcurementBlackoutDates()).thenReturn(Set.of(
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 24)
        ));

        ProcurementSchedule schedule = schedulerService.createSchedule(
                "Packaging Film Roll", 28, 15, 3, 2, LocalDate.of(2027, 1, 12));

        ProcurementCycleNode m1 = schedule.getNodes().get(0);
        assertEquals(LocalDate.of(2026, 12, 28), m1.getOrderCutoffDate());
        assertEquals(LocalDate.of(2026, 12, 25), m1.getUnadjustedActionDate());
        assertEquals(LocalDate.of(2026, 12, 23), m1.getActionDate());
        assertTrue(m1.isHolidayAdjusted());
        assertEquals(2, m1.getHolidayAdjustedDays());
    }
}
