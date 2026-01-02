package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {
    Optional<Product> findBySku(String sku);
}
