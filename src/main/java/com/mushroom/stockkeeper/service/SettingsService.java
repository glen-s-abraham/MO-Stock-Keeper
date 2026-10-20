package com.mushroom.stockkeeper.service;

import com.mushroom.stockkeeper.model.AppSetting;
import com.mushroom.stockkeeper.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SettingsService {

    private final AppSettingRepository appSettingRepository;

    @Value("${app.print-server.url:http://192.168.1.53:8080}")
    private String defaultPrintServerUrl = "http://192.168.1.53:8080";

    @Value("${app.print-server.hide-server-print-on-workstation:true}")
    private boolean hideServerPrintOnWorkstation = true;

    @Value("${app.print-server.hide-client-print-on-remote:true}")
    private boolean hideClientPrintOnRemote = true;

    public static final String KEY_COMPANY_NAME = "company_name";
    public static final String KEY_CONTACT_NUMBER = "company_phone";
    public static final String KEY_REGISTERED_OFFICE = "company_registered_office";
    public static final String KEY_CUSTOMER_CARE = "company_customer_care";
    public static final String KEY_LABEL_SHEET_SIZE = "label_sheet_size";
    public static final String KEY_TARGET_PRINTER = "target_printer";
    public static final String KEY_PRINT_SERVER_URL = "print_server_url";
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

    public String getRegisteredOfficeAddress() {
        return appSettingRepository.findBySettingKey(KEY_REGISTERED_OFFICE)
                .map(AppSetting::getSettingValue)
                .orElse(null);
    }

    public String getCustomerCareAddress() {
        return appSettingRepository.findBySettingKey(KEY_CUSTOMER_CARE)
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

    public String getPrintServerUrl() {
        return appSettingRepository.findBySettingKey(KEY_PRINT_SERVER_URL)
                .map(AppSetting::getSettingValue)
                .filter(val -> val != null && !val.trim().isEmpty())
                .map(String::trim)
                .orElse(defaultPrintServerUrl);
    }

    public String getDefaultPrintServerUrl() {
        return defaultPrintServerUrl;
    }

    public boolean isHideServerPrintOnWorkstation() {
        return hideServerPrintOnWorkstation;
    }

    public void setHideServerPrintOnWorkstation(boolean hideServerPrintOnWorkstation) {
        this.hideServerPrintOnWorkstation = hideServerPrintOnWorkstation;
    }

    public boolean isHideClientPrintOnRemote() {
        return hideClientPrintOnRemote;
    }

    public void setHideClientPrintOnRemote(boolean hideClientPrintOnRemote) {
        this.hideClientPrintOnRemote = hideClientPrintOnRemote;
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

    @jakarta.annotation.PostConstruct
    public void migrateDummyAddresses() {
        String oldAddress = appSettingRepository.findBySettingKey("company_address")
                .map(AppSetting::getSettingValue)
                .orElse("");

        if (!oldAddress.trim().isEmpty()) {
            Optional<AppSetting> regOpt = appSettingRepository.findBySettingKey(KEY_REGISTERED_OFFICE);
            if (regOpt.isPresent() && regOpt.get().getSettingValue().contains("123 Farm Lane")) {
                AppSetting reg = regOpt.get();
                reg.setSettingValue(oldAddress);
                appSettingRepository.save(reg);
            }

            Optional<AppSetting> careOpt = appSettingRepository.findBySettingKey(KEY_CUSTOMER_CARE);
            if (careOpt.isPresent() && careOpt.get().getSettingValue().contains("7, Landscape Shire")) {
                AppSetting care = careOpt.get();
                care.setSettingValue(oldAddress);
                appSettingRepository.save(care);
            }
        }
    }
}
