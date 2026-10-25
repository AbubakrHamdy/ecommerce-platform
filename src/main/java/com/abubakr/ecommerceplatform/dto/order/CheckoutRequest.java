package com.abubakr.ecommerceplatform.dto.order;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull
        Long addressId,

        @NotNull
        PaymentProvider paymentProvider
) {}
