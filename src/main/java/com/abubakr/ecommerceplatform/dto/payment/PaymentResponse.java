package com.abubakr.ecommerceplatform.dto.payment;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        PaymentProvider provider,
        PaymentStatus status,
        String transactionId,
        BigDecimal amount,
        Instant createdAt
) {}
