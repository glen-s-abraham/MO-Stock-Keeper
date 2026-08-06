package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/harvest")
    public String harvestDashboard(
            @RequestParam(name = "tab", defaultValue = "monthly") String tab,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "year", required = false) Integer year,
            Model model) {

        // Provide common data
        model.addAttribute("lifetimeTotals", dashboardService.getLifetimeHarvestTotals());
        model.addAttribute("activeTab", tab);
        model.addAttribute("availableYears", dashboardService.getAvailableHarvestYears());
        
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        int selectedYear = (year != null) ? year : currentYear;
        int selectedMonth = (month != null) ? month : currentMonth;

        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedMonth", selectedMonth);

        // Fetch specific data based on tab
        if ("monthly".equals(tab)) {
            model.addAttribute("snapshotTotals", dashboardService.getMonthlyHarvestTotals(selectedYear, selectedMonth));
        } else if ("yearly".equals(tab)) {
            model.addAttribute("snapshotTotals", dashboardService.getYearlyHarvestTotals(selectedYear));
        }

        return "dashboard/harvest";
    }

    @GetMapping("/harvest/chart-data")
    @org.springframework.web.bind.annotation.ResponseBody
    public com.mushroom.stockkeeper.dto.ChartResponseDto getChartData(
            @RequestParam(name = "tab") String tab,
            @RequestParam(name = "distribution", defaultValue = "daily") String distribution,
            @RequestParam(name = "year") int year,
            @RequestParam(name = "month", required = false) Integer month) {
        
        // If tab is monthly but month isn't provided, default to current month
        if ("monthly".equals(tab) && month == null) {
            month = LocalDate.now().getMonthValue();
        }
        
        return dashboardService.getChartData(tab, distribution, year, month);
    }
}
