package com.abubakr.ecommerceplatform.gateway;

// A verified webhook, translated into the provider-independent outcome the rest of the app understands.
public record PaymentEvent(
        String eventId,
        Type type,
        String sessionId,
        String transactionId,
        long amountMinor,
        String currency
) {

    public enum Type {
        SUCCEEDED,
        FAILED,
        EXPIRED
    }
}
