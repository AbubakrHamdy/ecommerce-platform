package com.abubakr.ecommerceplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ShippingAddress(
        @Column(name = "shipping_street", nullable = false, length = 150)
        String street,

        @Column(name = "shipping_city", nullable = false, length = 100)
        String city,

        @Column(name = "shipping_state", length = 100)
        String state,

        @Column(name = "shipping_postal_code", nullable = false, length = 20)
        String postalCode,

        @Column(name = "shipping_country", nullable = false, length = 100)
        String country
) {

    public static ShippingAddress copyOf(Address address) {
        return new ShippingAddress(address.getStreet(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry());
    }
}
