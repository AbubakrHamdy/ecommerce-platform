package com.abubakr.ecommerceplatform.dto.order;

import com.abubakr.ecommerceplatform.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt
) {}
