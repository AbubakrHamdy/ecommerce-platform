package com.abubakr.ecommerceplatform.dto.order;

import com.abubakr.ecommerceplatform.dto.user.AddressResponse;
import com.abubakr.ecommerceplatform.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        AddressResponse shippingAddress,
        Instant createdAt
) {}
