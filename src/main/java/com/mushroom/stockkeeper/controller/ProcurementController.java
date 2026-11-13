package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.service.ProcurementSchedulerService;
import com.mushroom.stockkeeper.service.SettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/procurement")
public class ProcurementController {

    private final ProcurementSchedulerService schedulerService;
    private final SettingsService settingsService;

    public ProcurementController(ProcurementSchedulerService schedulerService,
                                 SettingsService settingsService) {
        this.schedulerService = schedulerService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long scheduleId, Model model) {
        List<ProcurementSchedule> schedules = schedulerService.getActiveSchedules();
        List<ProcurementSchedulerService.HubScheduleItem> hubItems = schedulerService.getHubScheduleItems();

        ProcurementSchedule selectedSchedule = null;
        if (scheduleId != null) {
            selectedSchedule = schedules.stream()
                    .filter(s -> s.getId().equals(scheduleId))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedSchedule == null && !schedules.isEmpty()) {
            selectedSchedule = schedules.get(0);
        }

        LocalDate today = LocalDate.now();
        model.addAttribute("schedules", schedules);
        model.addAttribute("hubItems", hubItems);
        model.addAttribute("selectedSchedule", selectedSchedule);
        model.addAttribute("today", today);
        if (selectedSchedule != null) {
            model.addAttribute("earliestDeliveryDate", today.plusDays(selectedSchedule.getVendorLeadTime()));
        }
        model.addAttribute("blackoutDatesRaw", settingsService.getProcurementBlackoutDatesRaw());
        model.addAttribute("blackoutDates", settingsService.getProcurementBlackoutDates());

        return "procurement/index";
    }

    @PostMapping("/schedules")
    public String createSchedule(@RequestParam String scheduleName,
                                 @RequestParam int supplyLastingDays,
                                 @RequestParam int vendorLeadTime,
                                 @RequestParam int actionBuffer,
                                 @RequestParam int preferredDeliveryDay,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate initialDeliveryDate,
                                 RedirectAttributes redirectAttributes) {
        try {
            ProcurementSchedule created = schedulerService.createSchedule(
                    scheduleName,
                    supplyLastingDays,
                    vendorLeadTime,
                    actionBuffer,
                    preferredDeliveryDay,
                    initialDeliveryDate);
            redirectAttributes.addFlashAttribute("toastMessage",
                    "Schedule \"" + created.getScheduleName() + "\" created with 12-month projection.");
            return "redirect:/procurement?scheduleId=" + created.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/procurement";
        }
    }

    @PostMapping("/schedules/{scheduleId}/override")
    public String overrideDeliveryDate(@PathVariable Long scheduleId,
                                       @RequestParam int cycleIndex,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newDeliveryDate,
                                       RedirectAttributes redirectAttributes) {
        try {
            ProcurementSchedulerService.OverrideResult result =
                    schedulerService.overrideDeliveryDate(scheduleId, cycleIndex, newDeliveryDate);
            redirectAttributes.addFlashAttribute("toastMessage", result.toastMessage());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/procurement?scheduleId=" + scheduleId;
    }

    @PostMapping("/api/schedules/{scheduleId}/override")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> overrideDeliveryDateApi(
            @PathVariable Long scheduleId,
            @RequestParam int cycleIndex,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newDeliveryDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            ProcurementSchedulerService.OverrideResult result =
                    schedulerService.overrideDeliveryDate(scheduleId, cycleIndex, newDeliveryDate);
            body.put("success", true);
            body.put("shiftedDays", result.shiftedDays());
            body.put("message", result.toastMessage());
            body.put("scheduleId", scheduleId);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            body.put("success", false);
            body.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @PostMapping("/blackout-dates")
    public String updateBlackoutDates(@RequestParam(defaultValue = "") String blackoutDates,
                                      @RequestParam(required = false) Long scheduleId,
                                      RedirectAttributes redirectAttributes) {
        settingsService.setProcurementBlackoutDates(blackoutDates);
        schedulerService.recalculateAllActiveSchedules();
        redirectAttributes.addFlashAttribute("toastMessage",
                "Holiday Blackout Dates updated. Active 12-month schedules recalculated.");
        if (scheduleId != null) {
            return "redirect:/procurement?scheduleId=" + scheduleId;
        }
        return "redirect:/procurement";
    }

    @PostMapping("/schedules/{scheduleId}/delete")
    public String deleteSchedule(@PathVariable Long scheduleId, RedirectAttributes redirectAttributes) {
        try {
            schedulerService.deactivateSchedule(scheduleId);
            redirectAttributes.addFlashAttribute("toastMessage", "Schedule archived from active Hub View.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/procurement";
    }
}
