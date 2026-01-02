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

    private final com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository;
    private final com.mushroom.stockkeeper.repository.InventoryUnitRepository inventoryUnitRepository;

    public CustomerController(CustomerRepository customerRepository,
            com.mushroom.stockkeeper.repository.SalesOrderRepository orderRepository,
            com.mushroom.stockkeeper.repository.CreditNoteRepository creditNoteRepository,
            com.mushroom.stockkeeper.repository.InventoryUnitRepository inventoryUnitRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.inventoryUnitRepository = inventoryUnitRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", customerRepository.findAll());
        return "customers/list";
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer Id:" + id));

        // Calculate Product Stats
        long currentAllocatedUnits = inventoryUnitRepository.countBySalesOrderCustomerId(id);
        long totalReturned = creditNoteRepository.countByCustomerId(id);
        long returnedAndStillAllocated = inventoryUnitRepository
                .countBySalesOrderCustomerIdAndStatus(id, com.mushroom.stockkeeper.model.InventoryStatus.RETURNED);

        // Logic:
        // Total Sent = (Currently Allocated) + (Returned and Removed/Restocked/Spoiled)
        // Returned and Removed = Total Returned - Returned Still In Allocation
        long returnedAndRemoved = totalReturned - returnedAndStillAllocated;
        long totalSent = currentAllocatedUnits + returnedAndRemoved;

        double returnRate = 0.0;
        if (totalSent > 0) {
            returnRate = (double) totalReturned / totalSent * 100.0;
        }

        model.addAttribute("customer", customer);
        model.addAttribute("totalSent", totalSent);
        model.addAttribute("totalReturned", totalReturned);
        model.addAttribute("returnRate", String.format("%.1f", returnRate));

        // Add financial summary if needed (can reuse Collections logic or keep simple)
        // For now, MVP stats.

        return "customers/view";
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
