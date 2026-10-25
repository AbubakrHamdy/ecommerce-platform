package com.abubakr.ecommerceplatform.dto.product;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        int stock,
        boolean active,
        String thumbnailUrl,
        String categoryName
) {}
