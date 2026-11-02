package com.abubakr.ecommerceplatform.dto.order;

public record ShippingAddressResponse(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {}
