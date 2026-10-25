package com.abubakr.ecommerceplatform.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @NotNull @Min(0)
        Integer stock,

        @NotNull
        Long categoryId,

        boolean active
) {}
