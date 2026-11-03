package com.abubakr.ecommerceplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// orderUrl may contain {orderId}; it is the customer's order page in the frontend.
@ConfigurationProperties(prefix = "app.mail")
public record EmailProperties(
        String from,
        String storeName,
        String orderUrl
) {

    public String orderUrlFor(Long orderId) {
        return orderUrl.replace("{orderId}", String.valueOf(orderId));
    }
}
