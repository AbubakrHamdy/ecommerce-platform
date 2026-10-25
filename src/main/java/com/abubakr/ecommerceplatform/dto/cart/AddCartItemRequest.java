package com.abubakr.ecommerceplatform.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull
        Long productId,

        @NotNull @Min(1)
        Integer quantity
) {}
