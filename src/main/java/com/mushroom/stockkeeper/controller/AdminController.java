package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import com.mushroom.stockkeeper.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppSettingRepository settingRepository;
    private final SettingsService settingsService;

    public AdminController(AppSettingRepository settingRepository, SettingsService settingsService) {
        this.settingRepository = settingRepository;
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        // Fetch all as Map for easy access in View
        Map<String, String> settings = new HashMap<>(settingRepository.findAll().stream()
                .collect(Collectors.toMap(AppSetting::getSettingKey, AppSetting::getSettingValue, (a, b) -> b)));

        settings.putIfAbsent(SettingsService.KEY_PRINT_SERVER_URL, settingsService.getPrintServerUrl());

        model.addAttribute("settings", settings);
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> allParams, RedirectAttributes redirectAttributes) {
        // Dynamic Save: Iterate over all params found in the form
        // We filter out standard Spring/Tomcat params if any (like _csrf)
        // For simple app, saving all non-empty strings starting with known prefix or
        // just
        // everything except _csrf is safer.
        // Actually, let's trust the form.

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if (key.startsWith("_") || key.equals("csrf"))
                continue; // Skip internal

            AppSetting setting = settingRepository.findBySettingKey(key).orElse(new AppSetting(key, ""));
            setting.setSettingValue(val != null ? val : "");
            settingRepository.save(setting);
        }

        redirectAttributes.addFlashAttribute("success", "Settings updated successfully.");
        return "redirect:/admin/settings";
    }
}
