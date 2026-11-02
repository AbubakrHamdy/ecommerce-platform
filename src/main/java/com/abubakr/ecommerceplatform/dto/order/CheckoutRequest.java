package com.abubakr.ecommerceplatform.dto.order;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull
        Long addressId
) {}
