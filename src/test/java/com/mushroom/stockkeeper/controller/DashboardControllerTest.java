package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.dto.ProductHarvestAggregateDto;
import com.mushroom.stockkeeper.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testLifetimeHarvestTab() throws Exception {
        when(dashboardService.getLifetimeHarvestTotals()).thenReturn(List.of(
                new ProductHarvestAggregateDto("Button Mushroom", 500L)
        ));

        mockMvc.perform(get("/dashboard/harvest").param("tab", "lifetime"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/harvest"))
                .andExpect(model().attributeExists("lifetimeTotals"))
                .andExpect(model().attribute("activeTab", "lifetime"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testDefaultHarvestTab() throws Exception {
        when(dashboardService.getMonthlyHarvestTotals(anyInt(), anyInt())).thenReturn(List.of(
                new ProductHarvestAggregateDto("Button Mushroom", 200L)
        ));

        mockMvc.perform(get("/dashboard/harvest"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/harvest"))
                .andExpect(model().attributeExists("snapshotTotals"))
                .andExpect(model().attribute("activeTab", "monthly"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    public void testMonthlySnapshotTab() throws Exception {
        when(dashboardService.getMonthlyHarvestTotals(anyInt(), anyInt())).thenReturn(List.of(
                new ProductHarvestAggregateDto("Button Mushroom", 200L)
        ));

        mockMvc.perform(get("/dashboard/harvest").param("tab", "monthly").param("year", "2026").param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/harvest"))
                .andExpect(model().attributeExists("snapshotTotals"))
                .andExpect(model().attribute("activeTab", "monthly"));
    }
    
    @Test
    @WithMockUser(roles = "MANAGER")
    public void testManagerAccessToHarvestDashboard() throws Exception {
        mockMvc.perform(get("/dashboard/harvest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testChartDataEndpointDaily() throws Exception {
        when(dashboardService.getChartData("monthly", "daily", 2026, 8))
            .thenReturn(new com.mushroom.stockkeeper.dto.ChartResponseDto(List.of("1"), List.of()));

        mockMvc.perform(get("/dashboard/harvest/chart-data")
                .param("tab", "monthly")
                .param("distribution", "daily")
                .param("year", "2026")
                .param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_JSON));
    }
    
    @Test
    @WithMockUser(roles = "PACKER") // unauthorized
    public void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/dashboard/harvest"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Access Denied")));
    }
}
