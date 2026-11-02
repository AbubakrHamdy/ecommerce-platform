package com.abubakr.ecommerceplatform.config;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Only country is required; currency and providers are derived from it unless overridden.
@ConfigurationProperties(prefix = "app.store")
public record StoreProperties(
        String country,
        String currency,
        List<PaymentProvider> paymentProviders
) {}
