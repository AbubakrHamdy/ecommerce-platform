package com.abubakr.ecommerceplatform.dto.payment;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;

public record PaymentSessionResponse(
        Long paymentId,
        PaymentProvider provider,
        String checkoutUrl,
        String clientSecret
) {}
