package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.Product;
import com.mushroom.stockkeeper.repository.ProductRepository;
import com.mushroom.stockkeeper.repository.UOMRepository;
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
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UOMRepository uomRepository;

    private final com.mushroom.stockkeeper.repository.HarvestBatchRepository batchRepository;

    public ProductController(ProductRepository productRepository, UOMRepository uomRepository,
            com.mushroom.stockkeeper.repository.HarvestBatchRepository batchRepository) {
        this.productRepository = productRepository;
        this.uomRepository = uomRepository;
        this.batchRepository = batchRepository;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q, // Search
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search (Name, SKU)
            if (q != null && !q.trim().isEmpty()) {
                String likePattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("sku")), likePattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> productsPage = productRepository.findAll(spec, pageable);

        model.addAttribute("productsPage", productsPage);
        model.addAttribute("products", productsPage.getContent());

        return "products/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("uoms", uomRepository.findAll());
        return "products/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Product product) {
        productRepository.save(product);
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + id)));
        model.addAttribute("uoms", uomRepository.findAll());
        return "products/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        long usageCount = batchRepository.countByProductId(id);
        if (usageCount > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete Product. It is used in " + usageCount + " batches.");
            return "redirect:/products";
        }
        productRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Product deleted successfully.");
        return "redirect:/products";
    }
}
