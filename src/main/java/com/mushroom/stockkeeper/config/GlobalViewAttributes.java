package com.mushroom.stockkeeper.config;

import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewAttributes {

    private final AppSettingRepository settingRepository;

    public GlobalViewAttributes(AppSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @ModelAttribute
    public void addAttributes(Model model) {
        String symbol = settingRepository.findBySettingKey("currency_symbol")
                .map(AppSetting::getSettingValue)
                .filter(s -> !s.isEmpty())
                .orElse("₹");

        model.addAttribute("currencySymbol", symbol);

        // Also useful to expose company name globally?
        String companyName = settingRepository.findBySettingKey("company_name")
                .map(AppSetting::getSettingValue)
                .orElse("Mushroom Farm");
        model.addAttribute("companyName", companyName);
    }
}
