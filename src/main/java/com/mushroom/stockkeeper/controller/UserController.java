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
        // If ID is null, it's a new user, so encode password
        if (user.getId() == null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            // Edit mode: fetch existing to check if password changed or handle specifically
            // For simplicity, we'll re-encode if not empty, or keep old if empty?
            // standard approach: if managing users, admin sets new password.
            // Let's assume re-save for now. Real apps might separate password reset.
            // A simple approach for this MVP: Always encode what comes in.
            // If editing, ideally we don't want to re-hash the hash.
            User existing = userRepository.findById(user.getId()).orElse(null);
            if (existing != null && !user.getPassword().isEmpty()
                    && !user.getPassword().equals(existing.getPassword())) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            } else if (existing != null && (user.getPassword() == null || user.getPassword().isEmpty())) {
                user.setPassword(existing.getPassword());
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
