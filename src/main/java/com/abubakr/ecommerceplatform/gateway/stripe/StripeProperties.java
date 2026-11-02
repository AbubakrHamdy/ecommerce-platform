package com.abubakr.ecommerceplatform.gateway.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

// apiBase is only for pointing the SDK at stripe-mock in tests; leave it empty for real Stripe.
@ConfigurationProperties(prefix = "app.payments.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        String apiBase
) {}
