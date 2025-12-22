package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.Customer;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final com.mushroom.stockkeeper.repository.SalesOrderRepository orderRepository;

    public CustomerController(CustomerRepository customerRepository,
            com.mushroom.stockkeeper.repository.SalesOrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", customerRepository.findAll());
        return "customers/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("customer", new Customer());
        return "customers/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Customer customer) {
        customerRepository.save(customer);
        return "redirect:/customers";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer Id:" + id)));
        return "customers/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            long orderCount = orderRepository.countByCustomerId(id);
            if (orderCount > 0) {
                redirectAttributes.addFlashAttribute("error",
                        "Cannot delete customer. They have " + orderCount
                                + " associated Sales Orders (Draft/Invoiced/Cancelled).");
                return "redirect:/customers";
            }
            customerRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Customer deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete customer. They likely have active orders or financial records.");
        }
        return "redirect:/customers";
    }
}
