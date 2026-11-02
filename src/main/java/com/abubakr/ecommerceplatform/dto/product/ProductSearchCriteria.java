package com.abubakr.ecommerceplatform.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        @Schema(description = "Matches product name or description, case-insensitive")
        @Size(max = 100)
        String search,

        @Schema(description = "Includes products in all subcategories")
        @Positive
        Long categoryId,

        @DecimalMin("0.0")
        BigDecimal minPrice,

        @DecimalMin("0.0")
        BigDecimal maxPrice
) {}
