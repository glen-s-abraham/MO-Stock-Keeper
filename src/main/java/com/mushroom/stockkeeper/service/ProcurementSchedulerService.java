package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.ProcurementCycleNode;
import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.repository.ProcurementScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ProcurementSchedulerService {

    public static final int TOTAL_CYCLES = 12;

    private final ProcurementScheduleRepository scheduleRepository;
    private final SettingsService settingsService;
    private Clock clock;

    @Autowired
    public ProcurementSchedulerService(ProcurementScheduleRepository scheduleRepository,
                                       SettingsService settingsService) {
        this(scheduleRepository, settingsService, Clock.systemDefaultZone());
    }

    public ProcurementSchedulerService(ProcurementScheduleRepository scheduleRepository,
                                       SettingsService settingsService,
                                       Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.settingsService = settingsService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public record OverrideResult(ProcurementSchedule schedule, long shiftedDays, String toastMessage) {
    }

    public record HubScheduleItem(
            ProcurementSchedule schedule,
            ProcurementCycleNode currentCycleNode,
            String actionAlertStatus, // "OVERDUE", "UPCOMING", "SCHEDULED"
            boolean currentMonthActive,
            long stockoutRiskCount
    ) {
    }

    /**
     * Converts spec Preferred_Delivery_Day enum (0 = Sunday .. 6 = Saturday)
     * to java.time.DayOfWeek.
     */
    public static DayOfWeek toDayOfWeek(int preferredDeliveryDay) {
        if (preferredDeliveryDay < 0 || preferredDeliveryDay > 6) {
            throw new IllegalArgumentException("Preferred_Delivery_Day must be between 0 (Sunday) and 6 (Saturday)");
        }
        return preferredDeliveryDay == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(preferredDeliveryDay);
    }

    /**
     * Step 2: Snap backwards to the next occurring Preferred_Delivery_Day
     * on or immediately before the Theoretical_Need_Date.
     */
    public static LocalDate snapBackwardToPreferredDay(LocalDate theoreticalNeedDate, int preferredDeliveryDay) {
        DayOfWeek targetDay = toDayOfWeek(preferredDeliveryDay);
        return theoreticalNeedDate.with(TemporalAdjusters.previousOrSame(targetDay));
    }

    @Transactional(readOnly = true)
    public List<ProcurementSchedule> getActiveSchedules() {
        return scheduleRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<ProcurementSchedule> getActiveScheduleById(Long id) {
        return scheduleRepository.findByIdAndActiveTrue(id);
    }

    @Transactional(readOnly = true)
    public List<HubScheduleItem> getHubScheduleItems() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        List<ProcurementSchedule> schedules = getActiveSchedules();
        List<HubScheduleItem> items = new ArrayList<>(schedules.size());

        for (ProcurementSchedule schedule : schedules) {
            List<ProcurementCycleNode> nodes = schedule.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                continue;
            }
            ProcurementCycleNode focusNode = nodes.stream()
                    .filter(n -> !n.getActualDeliveryDate().isBefore(today))
                    .findFirst()
                    .orElse(nodes.get(0));

            String status;
            if (focusNode.getActionDate().isBefore(today)) {
                status = "OVERDUE";
            } else if (!focusNode.getActionDate().isAfter(today.plusDays(14))
                    || YearMonth.from(focusNode.getActionDate()).equals(currentMonth)
                    || YearMonth.from(focusNode.getActualDeliveryDate()).equals(currentMonth)) {
                status = "UPCOMING";
            } else {
                status = "SCHEDULED";
            }

            boolean inCurrentMonth = nodes.stream().anyMatch(n ->
                    YearMonth.from(n.getActionDate()).equals(currentMonth)
                            || YearMonth.from(n.getOrderCutoffDate()).equals(currentMonth)
                            || YearMonth.from(n.getActualDeliveryDate()).equals(currentMonth));

            long stockoutCount = nodes.stream().filter(ProcurementCycleNode::isStockoutRisk).count();
            items.add(new HubScheduleItem(schedule, focusNode, status, inCurrentMonth, stockoutCount));
        }
        return items;
    }

    @Transactional
    public ProcurementSchedule createSchedule(String scheduleName,
                                              int supplyLastingDays,
                                              int vendorLeadTime,
                                              int actionBuffer,
                                              int preferredDeliveryDay,
                                              LocalDate initialDeliveryDate) {
        validateScheduleParameters(scheduleName, supplyLastingDays, vendorLeadTime, actionBuffer, preferredDeliveryDay);

        LocalDate today = LocalDate.now(clock);

        LocalDate cycle1DeliveryDate;
        boolean cycle1Manual = false;
        if (initialDeliveryDate != null) {
            // Month 1 is the initial/received or anticipated anchor delivery date; allow any date without lead-time restriction
            cycle1DeliveryDate = initialDeliveryDate;
            cycle1Manual = true;
        } else {
            DayOfWeek targetDay = toDayOfWeek(preferredDeliveryDay);
            cycle1DeliveryDate = today.with(TemporalAdjusters.nextOrSame(targetDay));
        }

        ProcurementSchedule schedule = new ProcurementSchedule();
        schedule.setScheduleName(scheduleName.trim());
        schedule.setSupplyLastingDays(supplyLastingDays);
        schedule.setVendorLeadTime(vendorLeadTime);
        schedule.setActionBuffer(actionBuffer);
        schedule.setPreferredDeliveryDay(preferredDeliveryDay);
        schedule.setActive(true);

        Set<LocalDate> blackoutDates = settingsService.getProcurementBlackoutDates();
        List<ProcurementCycleNode> nodes = new ArrayList<>(TOTAL_CYCLES);

        for (int n = 1; n <= TOTAL_CYCLES; n++) {
            ProcurementCycleNode node = new ProcurementCycleNode();
            node.setSchedule(schedule);
            node.setCycleIndex(n);

            if (n == 1) {
                node.setTheoreticalNeedDate(cycle1DeliveryDate);
                node.setActualDeliveryDate(cycle1DeliveryDate);
                node.setManualOverride(cycle1Manual);
                node.setGapDays(supplyLastingDays);
                node.setStockoutRisk(false);
            } else {
                ProcurementCycleNode prevNode = nodes.get(n - 2);
                LocalDate theoreticalNeed = prevNode.getActualDeliveryDate().plusDays(supplyLastingDays);
                LocalDate actualDelivery = snapBackwardToPreferredDay(theoreticalNeed, preferredDeliveryDay);

                node.setTheoreticalNeedDate(theoreticalNeed);
                node.setActualDeliveryDate(actualDelivery);
                node.setManualOverride(false);

                int gap = (int) ChronoUnit.DAYS.between(prevNode.getActualDeliveryDate(), actualDelivery);
                node.setGapDays(gap);
                node.setStockoutRisk(gap > supplyLastingDays);
            }

            populateCutoffAndActionDates(node, vendorLeadTime, actionBuffer, blackoutDates);
            nodes.add(node);
        }

        schedule.setNodes(nodes);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public OverrideResult overrideDeliveryDate(Long scheduleId, int cycleIndex, LocalDate newDeliveryDate) {
        if (newDeliveryDate == null) {
            throw new IllegalArgumentException("New delivery date is required.");
        }
        if (cycleIndex < 1 || cycleIndex > TOTAL_CYCLES) {
            throw new IllegalArgumentException("Cycle index must be between 1 and " + TOTAL_CYCLES);
        }

        ProcurementSchedule schedule = scheduleRepository.findByIdAndActiveTrue(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        LocalDate today = LocalDate.now(clock);
        LocalDate earliestPossibleDelivery = today.plusDays(schedule.getVendorLeadTime());

        // Edge Case: The Lead Time Collision (applies to subsequent scheduled orders Months 2..12;
        // Month 1 is the initial received/anticipated anchor delivery date and can be freely adjusted)
        if (cycleIndex > 1 && newDeliveryDate.isBefore(earliestPossibleDelivery)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid Date: Vendor requires %d days lead time. Earliest possible delivery is %s.",
                    schedule.getVendorLeadTime(), earliestPossibleDelivery));
        }

        List<ProcurementCycleNode> nodes = schedule.getNodes();
        ProcurementCycleNode targetNode = nodes.get(cycleIndex - 1);

        if (cycleIndex > 1) {
            ProcurementCycleNode prevNode = nodes.get(cycleIndex - 2);
            if (!newDeliveryDate.isAfter(prevNode.getActualDeliveryDate())) {
                throw new IllegalArgumentException(String.format(
                        "Invalid Date: Cycle %d delivery date must be after Cycle %d delivery date (%s).",
                        cycleIndex, cycleIndex - 1, prevNode.getActualDeliveryDate()));
            }
        }

        LocalDate previousActualDelivery = targetNode.getActualDeliveryDate();
        long shiftedDays = ChronoUnit.DAYS.between(previousActualDelivery, newDeliveryDate);

        Set<LocalDate> blackoutDates = settingsService.getProcurementBlackoutDates();

        // Update overridden node k
        targetNode.setActualDeliveryDate(newDeliveryDate);
        targetNode.setManualOverride(true);
        if (cycleIndex == 1) {
            targetNode.setTheoreticalNeedDate(newDeliveryDate);
            targetNode.setGapDays(schedule.getSupplyLastingDays());
            targetNode.setStockoutRisk(false);
        } else {
            ProcurementCycleNode prevNode = nodes.get(cycleIndex - 2);
            int gap = (int) ChronoUnit.DAYS.between(prevNode.getActualDeliveryDate(), newDeliveryDate);
            targetNode.setGapDays(gap);
            targetNode.setStockoutRisk(gap > schedule.getSupplyLastingDays());
        }
        populateCutoffAndActionDates(targetNode, schedule.getVendorLeadTime(), schedule.getActionBuffer(), blackoutDates);

        // Cascade ("Ripple") recalculation from cycleIndex + 1 through 12
        for (int n = cycleIndex + 1; n <= TOTAL_CYCLES; n++) {
            ProcurementCycleNode prevNode = nodes.get(n - 2);
            ProcurementCycleNode currentNode = nodes.get(n - 1);

            LocalDate theoreticalNeed = prevNode.getActualDeliveryDate().plusDays(schedule.getSupplyLastingDays());
            LocalDate actualDelivery = snapBackwardToPreferredDay(theoreticalNeed, schedule.getPreferredDeliveryDay());

            currentNode.setTheoreticalNeedDate(theoreticalNeed);
            currentNode.setActualDeliveryDate(actualDelivery);
            currentNode.setManualOverride(false);

            int gap = (int) ChronoUnit.DAYS.between(prevNode.getActualDeliveryDate(), actualDelivery);
            currentNode.setGapDays(gap);
            currentNode.setStockoutRisk(gap > schedule.getSupplyLastingDays());

            populateCutoffAndActionDates(currentNode, schedule.getVendorLeadTime(), schedule.getActionBuffer(), blackoutDates);
        }

        ProcurementSchedule saved = scheduleRepository.save(schedule);
        String toastMessage = String.format(
                "Delivery shifted by %d days. Subsequent 12 months successfully recalculated.",
                shiftedDays);
        return new OverrideResult(saved, shiftedDays, toastMessage);
    }

    @Transactional
    public void recalculateAllActiveSchedules() {
        Set<LocalDate> blackoutDates = settingsService.getProcurementBlackoutDates();
        List<ProcurementSchedule> activeSchedules = scheduleRepository.findByActiveTrueOrderByCreatedAtDesc();
        for (ProcurementSchedule schedule : activeSchedules) {
            for (ProcurementCycleNode node : schedule.getNodes()) {
                populateCutoffAndActionDates(node, schedule.getVendorLeadTime(), schedule.getActionBuffer(), blackoutDates);
            }
            scheduleRepository.save(schedule);
        }
    }

    @Transactional
    public void deactivateSchedule(Long scheduleId) {
        ProcurementSchedule schedule = scheduleRepository.findByIdAndActiveTrue(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    private void populateCutoffAndActionDates(ProcurementCycleNode node,
                                              int vendorLeadTime,
                                              int actionBuffer,
                                              Set<LocalDate> blackoutDates) {
        // Step 3: Order_Cutoff_Date(n) = Actual_Delivery_Date(n) - Vendor_Lead_Time
        LocalDate cutoffDate = node.getActualDeliveryDate().minusDays(vendorLeadTime);
        node.setOrderCutoffDate(cutoffDate);

        // Step 4: Action_Date(n) = Order_Cutoff_Date(n) - Action_Buffer
        LocalDate unadjustedAction = cutoffDate.minusDays(actionBuffer);
        node.setUnadjustedActionDate(unadjustedAction);

        // Edge Case: Holiday Blackouts - subtract 1-2+ days if Action_Date falls on a blackout date
        LocalDate adjustedAction = unadjustedAction;
        int subtractedDays = 0;
        if (blackoutDates != null && !blackoutDates.isEmpty()) {
            while (blackoutDates.contains(adjustedAction)) {
                adjustedAction = adjustedAction.minusDays(1);
                subtractedDays++;
            }
        }
        node.setActionDate(adjustedAction);
        node.setHolidayAdjusted(subtractedDays > 0);
        node.setHolidayAdjustedDays(subtractedDays);
    }

    private void validateScheduleParameters(String scheduleName,
                                            int supplyLastingDays,
                                            int vendorLeadTime,
                                            int actionBuffer,
                                            int preferredDeliveryDay) {
        if (scheduleName == null || scheduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Schedule_Name is required.");
        }
        if (supplyLastingDays < 7) {
            throw new IllegalArgumentException("Supply_Lasting_Days must be at least 7 calendar days.");
        }
        if (vendorLeadTime < 1) {
            throw new IllegalArgumentException("Vendor_Lead_Time must be at least 1 day.");
        }
        if (actionBuffer < 0) {
            throw new IllegalArgumentException("Action_Buffer cannot be negative.");
        }
        if (preferredDeliveryDay < 0 || preferredDeliveryDay > 6) {
            throw new IllegalArgumentException("Preferred_Delivery_Day must be between 0 (Sunday) and 6 (Saturday).");
        }
    }
}
