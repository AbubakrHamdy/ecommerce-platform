package com.abubakr.ecommerceplatform.gateway.paymob;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// All values come from the Paymob dashboard; notificationUrl is this API's public webhook URL.
@ConfigurationProperties(prefix = "app.payments.paymob")
public record PaymobProperties(
        String baseUrl,
        String secretKey,
        String publicKey,
        String hmacSecret,
        List<Integer> integrationIds,
        String notificationUrl
) {}
