package com.abubakr.ecommerceplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.orders")
public record OrderProperties(
        Duration paymentTimeout,
        Duration expiryCheckInterval
) {}
