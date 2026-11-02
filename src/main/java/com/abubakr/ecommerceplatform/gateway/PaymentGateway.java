package com.abubakr.ecommerceplatform.gateway;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;

import java.util.Optional;

// One implementation per payment provider. Adding a provider means adding one class.
public interface PaymentGateway {

    PaymentProvider provider();

    // False when credentials are missing; the provider is then simply not offered.
    boolean isConfigured();

    CheckoutSession createSession(Order order, Payment payment);

    // Verifies the signature (throws InvalidWebhookException) and returns empty for event types we don't act on.
    Optional<PaymentEvent> parseWebhook(WebhookRequest request);

    // Best effort: closes a session at the provider so it can no longer be paid. Must not throw.
    void expireSession(Payment payment);
}
