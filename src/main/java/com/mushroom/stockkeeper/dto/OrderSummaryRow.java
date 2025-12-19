package com.mushroom.stockkeeper.dto;

import java.math.BigDecimal;

public record OrderSummaryRow(
                Long productId,
                String productName,
                long quantity,
                BigDecimal unitPrice,
                BigDecimal subtotal) {
}
