package com.abubakr.ecommerceplatform.gateway;

import java.time.Instant;

public record CheckoutSession(
        String sessionId,
        String checkoutUrl,
        Instant expiresAt
) {}
