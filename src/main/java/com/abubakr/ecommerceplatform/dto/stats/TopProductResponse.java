package com.abubakr.ecommerceplatform.dto.stats;

import java.math.BigDecimal;

public record TopProductResponse(
        Long productId,
        String productName,
        long unitsSold,
        BigDecimal revenue
) {}
