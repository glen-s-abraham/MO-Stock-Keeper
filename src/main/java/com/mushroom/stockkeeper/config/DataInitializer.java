package com.mushroom.stockkeeper.config;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner init(UserRepository userRepo,
            ProductRepository productRepo,
            UOMRepository uomRepo,
            CustomerRepository customerRepo,
            com.mushroom.stockkeeper.service.SettingsService settingsService,
            PasswordEncoder encoder) {
        return args -> {
            // Create Admin User
            if (userRepo.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("password"));
                admin.setRole(UserRole.ADMIN);
                admin.setFullName("System Admin");
                userRepo.save(admin);

                // Other roles for testing
                User packer = new User();
                packer.setUsername("packer");
                packer.setPassword(encoder.encode("password"));
                packer.setRole(UserRole.PACKER);
                packer.setFullName("Packer User");
                userRepo.save(packer);

                User sales = new User();
                sales.setUsername("sales");
                sales.setPassword(encoder.encode("password"));
                sales.setRole(UserRole.SALES);
                sales.setFullName("Sales User");
                userRepo.save(sales);
            }

            // Default UOMs
            if (uomRepo.count() == 0) {
                UOM box = new UOM();
                box.setCode("BOX");
                box.setDescription("Standard Mushroom Box");
                uomRepo.save(box);

                UOM kg = new UOM();
                kg.setCode("KG");
                kg.setDescription("Kilogram");
                uomRepo.save(kg);
            }

            // Sample Customer
            if (customerRepo.count() == 0) {
                Customer c = new Customer();
                c.setName("SuperMarket Chain A");
                c.setAddress("123 Market St");
                customerRepo.save(c);
            }

            // Ensure Walk-in Guest Exists
            if (customerRepo.findByName("Walk-in Guest").isEmpty()) {
                Customer guest = new Customer();
                guest.setName("Walk-in Guest");
                guest.setAddress("N/A");
                guest.setHidden(true); // Don't show in standard lists
                customerRepo.save(guest);
            }

            // Sample Product
            if (productRepo.count() == 0) {
                UOM boxUom = uomRepo.findByCode("BOX").orElse(null);
                if (boxUom == null && uomRepo.count() > 0) {
                    boxUom = uomRepo.findAll().stream().filter(u -> "BOX".equals(u.getCode())).findFirst().orElse(null);
                }

                if (boxUom != null) {
                    Product p = new Product();
                    p.setName("Button Mushrooms (Premium)");
                    p.setSku("BTN-001");
                    p.setDescription("Fresh white button mushrooms");
                    p.setUom(boxUom);
                    p.setDefaultExpiryDays(5);
                    p.setFssaiLicenseNumber("10012011000123");
                    p.setMrp(new java.math.BigDecimal("105.00"));
                    p.setHasNutritionValues(true);
                    p.setNutritionBaseUnitValue(new java.math.BigDecimal("100.00"));
                    p.setNutritionBaseUnitType("g");

                    NutritionLineItem energy = new NutritionLineItem();
                    energy.setProduct(p);
                    energy.setComponentName("Energy Value");
                    energy.setAmount(new java.math.BigDecimal("24.45"));
                    energy.setMeasurementUnit("kcal");
                    energy.setDisplayOrder(1);

                    NutritionLineItem protein = new NutritionLineItem();
                    protein.setProduct(p);
                    protein.setComponentName("Protein");
                    protein.setAmount(new java.math.BigDecimal("3.62"));
                    protein.setMeasurementUnit("g");
                    protein.setDisplayOrder(2);

                    NutritionLineItem carbs = new NutritionLineItem();
                    carbs.setProduct(p);
                    carbs.setComponentName("Carbohydrates");
                    carbs.setAmount(new java.math.BigDecimal("4.15"));
                    carbs.setMeasurementUnit("g");
                    carbs.setDisplayOrder(3);

                    NutritionLineItem fat = new NutritionLineItem();
                    fat.setProduct(p);
                    fat.setComponentName("Total Fat");
                    fat.setAmount(new java.math.BigDecimal("0.34"));
                    fat.setMeasurementUnit("g");
                    fat.setDisplayOrder(4);

                    NutritionLineItem fiber = new NutritionLineItem();
                    fiber.setProduct(p);
                    fiber.setComponentName("Dietary Fiber");
                    fiber.setAmount(new java.math.BigDecimal("2.20"));
                    fiber.setMeasurementUnit("g");
                    fiber.setDisplayOrder(5);

                    NutritionLineItem sugar = new NutritionLineItem();
                    sugar.setProduct(p);
                    sugar.setComponentName("Total Sugars");
                    sugar.setAmount(new java.math.BigDecimal("0.20"));
                    sugar.setMeasurementUnit("g");
                    sugar.setDisplayOrder(6);

                    p.getNutritionLineItems().add(energy);
                    p.getNutritionLineItems().add(protein);
                    p.getNutritionLineItems().add(carbs);
                    p.getNutritionLineItems().add(fat);
                    p.getNutritionLineItems().add(fiber);
                    p.getNutritionLineItems().add(sugar);

                    productRepo.save(p);
                }
            }

            // Global Settings
            String fallbackAddress = settingsService.getCompanyName() != null ? "" : "123 Farm Lane, Village Bhatpal, Canacona, Goa 403702";
            try {
                fallbackAddress = settingsService.getRegisteredOfficeAddress(); // check if empty
                if (fallbackAddress == null || fallbackAddress.isEmpty()) {
                     fallbackAddress = "123 Farm Lane, Village Bhatpal, Canacona, Goa 403702";
                }
            } catch (Exception e) {}
            // Wait, this is DataInitializer. Better to just remove the hardcoded Goa address entirely 
            // if the user already has a company_address. Let's just do it directly with Repositories if we had them.
            // Since we have settingsService, let's use it properly.
            
            // To be completely safe and avoid wiping their custom address in fresh setups:
            if (settingsService.getRegisteredOfficeAddress() == null || settingsService.getRegisteredOfficeAddress().isEmpty()) {
                settingsService.updateSetting(com.mushroom.stockkeeper.service.SettingsService.KEY_REGISTERED_OFFICE, "123 Farm Lane, Village Bhatpal, Canacona, Goa 403702");
            }
            if (settingsService.getCustomerCareAddress() == null || settingsService.getCustomerCareAddress().isEmpty()) {
                settingsService.updateSetting(com.mushroom.stockkeeper.service.SettingsService.KEY_CUSTOMER_CARE, "7, Landscape Shire, Caranzalem, Goa 403402");
            }
            if (settingsService.getContactNumber() == null || settingsService.getContactNumber().isEmpty()) {
                settingsService.updateSetting(com.mushroom.stockkeeper.service.SettingsService.KEY_CONTACT_NUMBER, "+91 8322465111");
            }
            if (settingsService.getCompanyName() == null || settingsService.getCompanyName().isEmpty()) {
                settingsService.updateSetting(com.mushroom.stockkeeper.service.SettingsService.KEY_COMPANY_NAME, "Zuari Foods & Farms Pvt. Ltd.");
            }
        };
    }
}
