package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SettingsService {

    private final AppSettingRepository appSettingRepository;

    public static final String KEY_COMPANY_NAME = "company_name";
    public static final String KEY_CONTACT_NUMBER = "company_phone";
    public static final String KEY_LABEL_SHEET_SIZE = "label_sheet_size";
    public static final String KEY_TARGET_PRINTER = "target_printer";
    public static final String KEY_CUSTOM_LABEL_WIDTH = "custom_label_width";
    public static final String KEY_CUSTOM_LABEL_HEIGHT = "custom_label_height";

    public SettingsService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    public String getCompanyName() {
        return appSettingRepository.findBySettingKey(KEY_COMPANY_NAME)
                .map(AppSetting::getSettingValue)
                .orElse(null);
    }

    public String getContactNumber() {
        return appSettingRepository.findBySettingKey(KEY_CONTACT_NUMBER)
                .map(AppSetting::getSettingValue)
                .orElse(null);
    }

    public String getLabelSheetSize() {
        return appSettingRepository.findBySettingKey(KEY_LABEL_SHEET_SIZE)
                .map(AppSetting::getSettingValue)
                .orElse("A4_40");
    }

    public String getTargetPrinter() {
        return appSettingRepository.findBySettingKey(KEY_TARGET_PRINTER)
                .map(AppSetting::getSettingValue)
                .orElse("");
    }

    public Integer getCustomLabelWidth() {
        return appSettingRepository.findBySettingKey(KEY_CUSTOM_LABEL_WIDTH)
                .map(AppSetting::getSettingValue)
                .map(val -> {
                    try {
                        return val.isEmpty() ? 100 : Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        return 100;
                    }
                })
                .orElse(100);
    }

    public Integer getCustomLabelHeight() {
        return appSettingRepository.findBySettingKey(KEY_CUSTOM_LABEL_HEIGHT)
                .map(AppSetting::getSettingValue)
                .map(val -> {
                    try {
                        return val.isEmpty() ? 150 : Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        return 150;
                    }
                })
                .orElse(150);
    }

    // Generic helper used internally or by other services if needed
    public void updateSetting(String key, String value) {
        Optional<AppSetting> existing = appSettingRepository.findBySettingKey(key);
        if (existing.isPresent()) {
            AppSetting setting = existing.get();
            setting.setSettingValue(value != null ? value : "");
            appSettingRepository.save(setting);
        } else {
            AppSetting setting = new AppSetting(key, value != null ? value : "");
            appSettingRepository.save(setting);
        }
    }
}
