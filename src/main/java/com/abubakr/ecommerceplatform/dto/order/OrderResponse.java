package com.abubakr.ecommerceplatform.dto.order;

import com.abubakr.ecommerceplatform.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        ShippingAddressResponse shippingAddress,
        Instant createdAt
) {}
