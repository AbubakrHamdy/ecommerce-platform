package com.abubakr.ecommerceplatform.gateway;

import org.springframework.http.HttpHeaders;

import java.util.Map;

// The raw, unparsed body is kept on purpose: signatures are computed over the exact bytes the provider sent.
public record WebhookRequest(
        String body,
        HttpHeaders headers,
        Map<String, String> queryParams
) {}
