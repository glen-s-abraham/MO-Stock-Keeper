package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.ProcurementCycleNode;
import com.mushroom.stockkeeper.model.ProcurementSchedule;
import com.mushroom.stockkeeper.service.ProcurementSchedulerService;
import com.mushroom.stockkeeper.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProcurementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcurementSchedulerService schedulerService;

    @MockBean
    private SettingsService settingsService;

    private ProcurementSchedule buildSampleSchedule() {
        ProcurementSchedule schedule = new ProcurementSchedule();
        schedule.setId(1L);
        schedule.setScheduleName("Raw Substrate - 175kg");
        schedule.setSupplyLastingDays(28);
        schedule.setVendorLeadTime(15);
        schedule.setActionBuffer(3);
        schedule.setPreferredDeliveryDay(6);
        schedule.setActive(true);

        List<ProcurementCycleNode> nodes = new ArrayList<>();
        LocalDate baseDelivery = LocalDate.of(2026, 12, 5);
        for (int n = 1; n <= 12; n++) {
            ProcurementCycleNode node = new ProcurementCycleNode();
            node.setId((long) n);
            node.setSchedule(schedule);
            node.setCycleIndex(n);
            LocalDate delivery = baseDelivery.plusDays((long) (n - 1) * 28);
            node.setTheoreticalNeedDate(delivery);
            node.setActualDeliveryDate(delivery);
            node.setOrderCutoffDate(delivery.minusDays(15));
            node.setUnadjustedActionDate(delivery.minusDays(18));
            node.setActionDate(delivery.minusDays(18));
            node.setGapDays(n == 2 ? 30 : 28);
            node.setStockoutRisk(n == 2);
            nodes.add(node);
        }
        schedule.setNodes(nodes);
        return schedule;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void index_ShouldRenderHubViewAnd12MonthTimelineAndNavigationTab() throws Exception {
        ProcurementSchedule schedule = buildSampleSchedule();
        ProcurementSchedulerService.HubScheduleItem hubItem =
                new ProcurementSchedulerService.HubScheduleItem(
                        schedule, schedule.getNodes().get(0), "UPCOMING", true, 1L);

        when(schedulerService.getActiveSchedules()).thenReturn(List.of(schedule));
        when(schedulerService.getHubScheduleItems()).thenReturn(List.of(hubItem));
        when(settingsService.getProcurementBlackoutDatesRaw()).thenReturn("2026-12-25");
        when(settingsService.getProcurementBlackoutDates()).thenReturn(Set.of(LocalDate.of(2026, 12, 25)));

        mockMvc.perform(get("/procurement"))
                .andExpect(status().isOk())
                .andExpect(view().name("procurement/index"))
                .andExpect(content().string(containsString("Adaptive Rolling Procurement Scheduler (ARPS)")))
                .andExpect(content().string(containsString("Raw Substrate - 175kg")))
                .andExpect(content().string(containsString("Month 1")))
                .andExpect(content().string(containsString("Month 12")))
                .andExpect(content().string(containsString(
                        "Stockout Risk: Gap is 30 days, supply only lasts 28. Increase order volume or pull delivery forward.")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createSchedule_ShouldInvokeServiceAndRedirectToTimeline() throws Exception {
        ProcurementSchedule schedule = buildSampleSchedule();
        when(schedulerService.createSchedule(
                eq("Raw Substrate - 175kg"), eq(28), eq(15), eq(3), eq(6), isNull()))
                .thenReturn(schedule);

        mockMvc.perform(post("/procurement/schedules")
                        .param("scheduleName", "Raw Substrate - 175kg")
                        .param("supplyLastingDays", "28")
                        .param("vendorLeadTime", "15")
                        .param("actionBuffer", "3")
                        .param("preferredDeliveryDay", "6")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procurement?scheduleId=1"))
                .andExpect(flash().attributeExists("toastMessage"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideDeliveryDate_WhenValid_ShouldSetToastMessageAndRedirect() throws Exception {
        ProcurementSchedule schedule = buildSampleSchedule();
        String expectedToast = "Delivery shifted by 4 days. Subsequent 12 months successfully recalculated.";
        when(schedulerService.overrideDeliveryDate(eq(1L), eq(1), eq(LocalDate.of(2026, 12, 9))))
                .thenReturn(new ProcurementSchedulerService.OverrideResult(schedule, 4L, expectedToast));

        mockMvc.perform(post("/procurement/schedules/1/override")
                        .param("cycleIndex", "1")
                        .param("newDeliveryDate", "2026-12-09")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procurement?scheduleId=1"))
                .andExpect(flash().attribute("toastMessage", expectedToast));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideDeliveryDate_WhenLeadTimeCollision_ShouldFlashErrorMessage() throws Exception {
        String collisionError = "Invalid Date: Vendor requires 15 days lead time. Earliest possible delivery is 2026-11-28.";
        when(schedulerService.overrideDeliveryDate(eq(1L), eq(1), eq(LocalDate.of(2026, 11, 20))))
                .thenThrow(new IllegalArgumentException(collisionError));

        mockMvc.perform(post("/procurement/schedules/1/override")
                        .param("cycleIndex", "1")
                        .param("newDeliveryDate", "2026-11-20")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procurement?scheduleId=1"))
                .andExpect(flash().attribute("error", collisionError));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overrideDeliveryDateApi_ShouldReturnJsonFeedback() throws Exception {
        ProcurementSchedule schedule = buildSampleSchedule();
        String expectedToast = "Delivery shifted by 2 days. Subsequent 12 months successfully recalculated.";
        when(schedulerService.overrideDeliveryDate(eq(1L), eq(1), eq(LocalDate.of(2026, 12, 7))))
                .thenReturn(new ProcurementSchedulerService.OverrideResult(schedule, 2L, expectedToast));

        mockMvc.perform(post("/procurement/api/schedules/1/override")
                        .param("cycleIndex", "1")
                        .param("newDeliveryDate", "2026-12-07")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.shiftedDays").value(2))
                .andExpect(jsonPath("$.message").value(expectedToast));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateBlackoutDates_ShouldPersistAndRecalculateActiveSchedules() throws Exception {
        mockMvc.perform(post("/procurement/blackout-dates")
                        .param("blackoutDates", "2026-12-25, 2026-08-26")
                        .param("scheduleId", "1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procurement?scheduleId=1"))
                .andExpect(flash().attributeExists("toastMessage"));

        verify(settingsService).setProcurementBlackoutDates("2026-12-25, 2026-08-26");
        verify(schedulerService).recalculateAllActiveSchedules();
    }
}
