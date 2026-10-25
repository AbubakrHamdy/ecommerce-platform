package com.abubakr.ecommerceplatform.dto.product;

import com.abubakr.ecommerceplatform.dto.category.CategorySummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        boolean active,
        CategorySummaryResponse category,
        List<ProductImageResponse> images,
        Instant createdAt
) {}
