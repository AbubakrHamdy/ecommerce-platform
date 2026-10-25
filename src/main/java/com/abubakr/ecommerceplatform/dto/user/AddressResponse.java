package com.abubakr.ecommerceplatform.dto.user;

public record AddressResponse(
        Long id,
        String street,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault
) {}
