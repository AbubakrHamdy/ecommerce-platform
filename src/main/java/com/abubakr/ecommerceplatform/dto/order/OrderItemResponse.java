package com.abubakr.ecommerceplatform.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productName,
        String productImageUrl,
        BigDecimal priceAtPurchase,
        int quantity,
        BigDecimal subtotal
) {}
