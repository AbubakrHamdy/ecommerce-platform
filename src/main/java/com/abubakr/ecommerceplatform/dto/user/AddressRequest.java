package com.abubakr.ecommerceplatform.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 150)
        String street,

        @NotBlank @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @NotBlank @Size(max = 20)
        String postalCode,

        @NotBlank @Size(max = 100)
        String country,

        boolean isDefault
) {}
