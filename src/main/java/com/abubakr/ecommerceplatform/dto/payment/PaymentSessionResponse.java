package com.abubakr.ecommerceplatform.dto.payment;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;

import java.time.Instant;

public record PaymentSessionResponse(
        Long paymentId,
        PaymentProvider provider,
        String checkoutUrl,
        Instant expiresAt
) {}
