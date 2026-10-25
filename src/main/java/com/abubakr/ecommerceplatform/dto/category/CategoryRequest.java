package com.abubakr.ecommerceplatform.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100)
        String name,

        Long parentId
) {}
