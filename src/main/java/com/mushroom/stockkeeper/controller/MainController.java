package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.service.ProcurementSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MainController {

    @Autowired(required = false)
    private ProcurementSchedulerService procurementSchedulerService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Dashboard");
        if (procurementSchedulerService != null) {
            List<ProcurementSchedulerService.HubScheduleItem> hubItems =
                    procurementSchedulerService.getHubScheduleItems();
            long overdueCount = hubItems.stream()
                    .filter(i -> "OVERDUE".equals(i.actionAlertStatus()))
                    .count();
            long upcomingCount = hubItems.stream()
                    .filter(i -> "UPCOMING".equals(i.actionAlertStatus()))
                    .count();
            model.addAttribute("procurementScheduleCount", hubItems.size());
            model.addAttribute("procurementOverdueCount", overdueCount);
            model.addAttribute("procurementUpcomingCount", upcomingCount);
        }
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
