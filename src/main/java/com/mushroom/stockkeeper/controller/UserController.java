package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.User;
import com.mushroom.stockkeeper.model.UserRole;
import com.mushroom.stockkeeper.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin/users/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", UserRole.values());
        return "admin/users/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute User user) {
        // Password Validation
        if (user.getId() == null) {
            // New User
            if (user.getPassword() == null || user.getPassword().trim().length() < 6) {
                // Ideally return binding error, but for quick fix throw exception or handle
                throw new IllegalArgumentException("Password must be at least 6 characters.");
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            // Edit mode
            User existing = userRepository.findById(user.getId()).orElse(null);
            if (existing != null) {
                if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                    if (user.getPassword().trim().length() < 6) {
                        throw new IllegalArgumentException("Password must be at least 6 characters.");
                    }
                    if (!user.getPassword().equals(existing.getPassword())) {
                        // Only re-encode if it's not the existing hash (unlikely collision but safe)
                        user.setPassword(passwordEncoder.encode(user.getPassword()));
                    }
                } else {
                    // Empty password = keep existing
                    user.setPassword(existing.getPassword());
                }
            }
        }
        userRepository.save(user);
        return "redirect:/admin/users";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("user",
                userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id)));
        model.addAttribute("roles", UserRole.values());
        return "admin/users/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "redirect:/admin/users";
    }
}
