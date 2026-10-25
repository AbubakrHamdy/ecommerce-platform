package com.abubakr.ecommerceplatform.dto.product;

public record ProductImageResponse(
        Long id,
        String url,
        boolean isPrimary,
        int displayOrder
) {}
