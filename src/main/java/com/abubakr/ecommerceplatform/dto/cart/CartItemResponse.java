package com.abubakr.ecommerceplatform.dto.cart;

import com.abubakr.ecommerceplatform.dto.product.ProductSummaryResponse;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        ProductSummaryResponse product,
        int quantity,
        BigDecimal subtotal
) {}
