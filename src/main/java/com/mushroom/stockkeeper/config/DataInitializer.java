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
                    // Fallback if findByCode not implemented or not found, though we just added it.
                    // But wait, uomRepo might not have findByCode? Let's assume it does or use
                    // findAll and filter,
                    // or just rely on the object reference if we were in the same transaction scope
                    // properly,
                    // but here we might need to fetch it.
                    // Actually, I can just use the 'box' object from above if I refactor slightly,
                    // but better to fetch it or ensure save returns the managed entity.
                    boxUom = uomRepo.findAll().stream().filter(u -> "BOX".equals(u.getCode())).findFirst().orElse(null);
                }

                if (boxUom != null) {
                    Product p = new Product();
                    p.setName("Button Mushrooms (Premium)");
                    p.setSku("BTN-001");
                    p.setDescription("Fresh white button mushrooms");
                    p.setUom(boxUom);
                    p.setDefaultExpiryDays(5);
                    productRepo.save(p);
                }
            }
        };
    }
}
