package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductHarvestAggregateDto {
    private String productName;
    private Long totalQuantity;
}
