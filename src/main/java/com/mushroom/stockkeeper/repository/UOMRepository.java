package com.mushroom.stockkeeper.repository;

import com.mushroom.stockkeeper.model.UOM;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UOMRepository extends JpaRepository<UOM, Long> {
    Optional<UOM> findByCode(String code);
}
