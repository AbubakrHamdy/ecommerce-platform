package com.abubakr.ecommerceplatform.dto.category;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        String parentName
) {}
