package com.mushroom.stockkeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesAggregateDto {
    private String productName;
    private Integer timeBucket; 
    private Long totalQuantity;
}
