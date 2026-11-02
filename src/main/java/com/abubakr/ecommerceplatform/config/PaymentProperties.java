package com.abubakr.ecommerceplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// URLs may contain {orderId}; the customer's browser returns there after paying or giving up (UX only, not proof).
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(
        Duration sessionTtl,
        String successUrl,
        String cancelUrl
) {

    public String successUrlFor(Long orderId) {
        return successUrl.replace("{orderId}", String.valueOf(orderId));
    }

    public String cancelUrlFor(Long orderId) {
        return cancelUrl.replace("{orderId}", String.valueOf(orderId));
    }
}
