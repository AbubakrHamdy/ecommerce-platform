package com.abubakr.ecommerceplatform.dto.store;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;

import java.util.List;

public record StoreResponse(
        String country,
        String currency,
        String timeZone,
        List<PaymentProvider> paymentProviders
) {}
