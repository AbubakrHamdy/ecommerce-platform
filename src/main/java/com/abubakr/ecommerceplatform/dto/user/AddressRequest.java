package com.abubakr.ecommerceplatform.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
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

        @Schema(description = "Make this the default address. The first address is always the default.")
        Boolean isDefault
) {}
