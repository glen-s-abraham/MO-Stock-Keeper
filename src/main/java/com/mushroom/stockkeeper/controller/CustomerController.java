package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.Customer;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

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
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q, // Search
            @RequestParam(defaultValue = "all") String type, // Filter by Type
            @RequestParam(required = false) Double minCredit, // Filter: Credit Limit > X
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always hide hidden customers for general list unless specifically requested?
            // Actually, existing logic showed 'isHiddenFalse' probably.
            // Let's check repository... repository had findByIsHiddenFalse
            // So we should filter out hidden ones by default unless maybe searching?
            // "Walk-in Guest" is hidden. We probably don't want to list them here.
            predicates.add(cb.isFalse(root.get("isHidden")));

            // Search (Name, Phone, Email, Contact Person)
            if (q != null && !q.trim().isEmpty()) {
                String likePattern = "%" + q.toLowerCase() + "%";
                Predicate searchPred = cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("phone")), likePattern),
                        cb.like(cb.lower(root.get("email")), likePattern),
                        cb.like(cb.lower(root.get("contactPerson")), likePattern));
                predicates.add(searchPred);
            }

            // Filter By Type
            if (!"all".equalsIgnoreCase(type) && type != null) {
                try {
                    com.mushroom.stockkeeper.model.CustomerType typeEnum = com.mushroom.stockkeeper.model.CustomerType
                            .valueOf(type.toUpperCase());
                    predicates.add(cb.equal(root.get("type"), typeEnum));
                } catch (Exception e) {
                    // Ignore invalid type
                }
            }

            // Filter by Minimum Credit Limit
            if (minCredit != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("creditLimit"), minCredit));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Customer> customerPage = customerRepository.findAll(spec, pageable);

        model.addAttribute("customerPage", customerPage);
        model.addAttribute("customers", customerPage.getContent());
        model.addAttribute("currentType", type);

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
