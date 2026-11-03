package com.abubakr.ecommerceplatform.dto.stats;

import java.math.BigDecimal;

// productName is the product's current name; revenue uses the prices customers actually paid.
public record TopProductResponse(
        Long productId,
        String productName,
        long unitsSold,
        BigDecimal revenue
) {}
