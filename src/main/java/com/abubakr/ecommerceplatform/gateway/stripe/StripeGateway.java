package com.abubakr.ecommerceplatform.gateway.stripe;

import com.abubakr.ecommerceplatform.config.PaymentProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.exception.InvalidWebhookException;
import com.abubakr.ecommerceplatform.exception.PaymentProviderException;
import com.abubakr.ecommerceplatform.gateway.CheckoutSession;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.PaymentGateway;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.abubakr.ecommerceplatform.service.Money;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Component
public class StripeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final StripeProperties stripe;
    private final PaymentProperties payments;
    private final StripeClient client;

    public StripeGateway(StripeProperties stripe, PaymentProperties payments) {
        this.stripe = stripe;
        this.payments = payments;
        this.client = isConfigured() ? buildClient(stripe) : null;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(stripe.secretKey()) && StringUtils.hasText(stripe.webhookSecret());
    }

    @Override
    public CheckoutSession createSession(Order order, Payment payment) {
        Instant expiresAt = Instant.now().plus(payments.sessionTtl());
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(payments.successUrlFor(order.getId()))
                .setCancelUrl(payments.cancelUrlFor(order.getId()))
                .setClientReferenceId(String.valueOf(order.getId()))
                .setCustomerEmail(order.getUser().getEmail())
                .setExpiresAt(expiresAt.getEpochSecond())
                .putMetadata("orderId", String.valueOf(order.getId()))
                .putMetadata("paymentId", String.valueOf(payment.getId()));
        for (OrderItem item : order.getItems()) {
            params.addLineItem(lineItem(item, payment.getCurrency()));
        }
        // If our request is retried after a timeout, Stripe returns the first session instead of creating another.
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("payment-" + payment.getId()).build();

        try {
            Session session = client.v1().checkout().sessions().create(params.build(), options);
            Instant sessionExpiry = session.getExpiresAt() == null ? expiresAt : Instant.ofEpochSecond(session.getExpiresAt());
            return new CheckoutSession(session.getId(), session.getUrl(), sessionExpiry);
        } catch (StripeException e) {
            throw new PaymentProviderException("Stripe could not create a checkout session", e);
        }
    }

    @Override
    public Optional<PaymentEvent> parseWebhook(WebhookRequest request) {
        String signature = request.headers().getFirst("Stripe-Signature");
        if (!StringUtils.hasText(signature)) {
            throw new InvalidWebhookException("Missing Stripe-Signature header");
        }
        Event event;
        try {
            // Also rejects signatures older than 5 minutes, so a captured request can't be replayed later.
            event = Webhook.constructEvent(request.body(), signature, stripe.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookException("Invalid Stripe signature", e);
        } catch (RuntimeException e) {
            throw new InvalidWebhookException("Malformed Stripe event", e);
        }

        PaymentEvent.Type type = switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> PaymentEvent.Type.SUCCEEDED;
            case "checkout.session.async_payment_failed" -> PaymentEvent.Type.FAILED;
            case "checkout.session.expired" -> PaymentEvent.Type.EXPIRED;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }

        Session session = session(event);
        // "completed" with an unpaid status means a delayed method (e.g. bank debit); wait for async_payment_succeeded.
        if (type == PaymentEvent.Type.SUCCEEDED && !"paid".equals(session.getPaymentStatus())) {
            return Optional.empty();
        }
        long amount = session.getAmountTotal() == null ? 0 : session.getAmountTotal();
        return Optional.of(new PaymentEvent(event.getId(), type, session.getId(), session.getPaymentIntent(),
                amount, session.getCurrency()));
    }

    @Override
    public void expireSession(Payment payment) {
        if (payment.getProviderSessionId() == null) {
            return;
        }
        try {
            client.v1().checkout().sessions().expire(payment.getProviderSessionId());
        } catch (StripeException | RuntimeException e) {
            log.warn("Could not expire Stripe session {}: {}", payment.getProviderSessionId(), e.getMessage());
        }
    }

    private static SessionCreateParams.LineItem lineItem(OrderItem item, String currency) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity((long) item.getQuantity())
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency.toLowerCase(Locale.ROOT))
                        .setUnitAmount(Money.toMinorUnits(item.getPriceAtPurchase(), currency))
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.getProductName())
                                .build())
                        .build())
                .build();
    }

    // Falls back to lenient parsing when the event's API version differs from the SDK's pinned version.
    private static Session session(Event event) {
        StripeObject object;
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            Optional<StripeObject> matchingVersion = deserializer.getObject();
            object = matchingVersion.isPresent() ? matchingVersion.get() : deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException | RuntimeException e) {
            // A 400 tells Stripe not to retry; letting this become a 500 would make it retry an unreadable event forever.
            throw new InvalidWebhookException("Unreadable Stripe event payload", e);
        }
        if (!(object instanceof Session session)) {
            throw new InvalidWebhookException("Stripe event does not contain a checkout session");
        }
        return session;
    }

    private static StripeClient buildClient(StripeProperties properties) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS);
        if (StringUtils.hasText(properties.apiBase())) {
            builder.setApiBase(properties.apiBase());
        }
        return builder.build();
    }
}
