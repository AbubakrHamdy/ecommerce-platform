package com.abubakr.ecommerceplatform.config;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Only country is required; currency and providers are derived from it unless overridden.
// timeZone decides what "a day" means in reports (IANA id such as Africa/Cairo); defaults to UTC.
@ConfigurationProperties(prefix = "app.store")
public record StoreProperties(
        String country,
        String currency,
        List<PaymentProvider> paymentProviders,
        String timeZone
) {}
