package com.abubakr.ecommerceplatform.gateway.stripe;

import com.abubakr.ecommerceplatform.config.PaymentProperties;
import com.abubakr.ecommerceplatform.exception.InvalidWebhookException;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeGatewayTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final StripeGateway gateway = new StripeGateway(
            new StripeProperties("sk_test_dummy", WEBHOOK_SECRET, null),
            new PaymentProperties(Duration.ofMinutes(31), "http://shop/orders/{orderId}", "http://shop/orders/{orderId}"));

    @Test
    void paidCheckoutSession_becomesASucceededEvent() {
        String payload = sessionEvent("evt_1", "checkout.session.completed", "paid");

        Optional<PaymentEvent> event = gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now()));

        assertThat(event).contains(new PaymentEvent("evt_1", PaymentEvent.Type.SUCCEEDED, "cs_test_1", "pi_1", 5997, "egp"));
    }

    @Test
    void completedButNotYetPaid_isIgnoredUntilTheAsyncResultArrives() {
        String payload = sessionEvent("evt_2", "checkout.session.completed", "unpaid");

        assertThat(gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now()))).isEmpty();
    }

    @Test
    void expiredSession_becomesAnExpiredEvent() {
        String payload = sessionEvent("evt_3", "checkout.session.expired", "unpaid");

        assertThat(gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now())))
                .get().extracting(PaymentEvent::type).isEqualTo(PaymentEvent.Type.EXPIRED);
    }

    @Test
    void eventTypesWeDontUse_areIgnored() {
        String payload = """
                {"id":"evt_4","object":"event","type":"customer.created","data":{"object":{"id":"cus_1","object":"customer"}}}""";

        assertThat(gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now()))).isEmpty();
    }

    @Test
    void payloadChangedAfterSigning_isRejected() {
        String payload = sessionEvent("evt_5", "checkout.session.completed", "paid");
        WebhookRequest signed = signed(payload, WEBHOOK_SECRET, Instant.now());
        WebhookRequest tampered = new WebhookRequest(payload.replace("5997", "1"), signed.headers(), Map.of());

        assertThatThrownBy(() -> gateway.parseWebhook(tampered)).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void signatureMadeWithAnotherSecret_isRejected() {
        String payload = sessionEvent("evt_6", "checkout.session.completed", "paid");

        assertThatThrownBy(() -> gateway.parseWebhook(signed(payload, "whsec_attacker", Instant.now())))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void oldSignature_isRejectedSoCapturedRequestsCantBeReplayed() {
        String payload = sessionEvent("evt_7", "checkout.session.completed", "paid");

        assertThatThrownBy(() -> gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now().minus(Duration.ofMinutes(10)))))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void missingSignatureHeader_isRejected() {
        WebhookRequest unsigned = new WebhookRequest(sessionEvent("evt_8", "checkout.session.completed", "paid"),
                new HttpHeaders(), Map.of());

        assertThatThrownBy(() -> gateway.parseWebhook(unsigned))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("Missing Stripe-Signature header");
    }

    @Test
    void withoutCredentials_stripeIsNotOffered() {
        StripeGateway unconfigured = new StripeGateway(new StripeProperties("", "", null),
                new PaymentProperties(Duration.ofMinutes(31), "x", "x"));

        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void eventFromAnOlderStripeApiVersion_isStillRead() {
        String payload = sessionEvent("evt_9", "checkout.session.completed", "paid", "2020-08-27");

        assertThat(gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now())))
                .get().extracting(PaymentEvent::type).isEqualTo(PaymentEvent.Type.SUCCEEDED);
    }

    @Test
    void validlySignedButUnreadableEvent_isRejectedInsteadOfRetriedForever() {
        String payload = """
                {"id":"evt_10","object":"event","type":"checkout.session.completed","data":{"object":{"id":"cs_1"}}}""";

        assertThatThrownBy(() -> gateway.parseWebhook(signed(payload, WEBHOOK_SECRET, Instant.now())))
                .isInstanceOf(InvalidWebhookException.class);
    }

    private static String sessionEvent(String eventId, String type, String paymentStatus) {
        return sessionEvent(eventId, type, paymentStatus, Stripe.API_VERSION);
    }

    private static String sessionEvent(String eventId, String type, String paymentStatus, String apiVersion) {
        return """
                {"id":"%s","object":"event","api_version":"%s","type":"%s","data":{"object":{
                  "id":"cs_test_1","object":"checkout.session","amount_total":5997,"currency":"egp",
                  "payment_status":"%s","payment_intent":"pi_1","client_reference_id":"7"}}}"""
                .formatted(eventId, apiVersion, type, paymentStatus);
    }

    // Stripe's scheme: header "t=<unix time>,v1=<hex HMAC-SHA256(secret, "<t>.<payload>")>".
    private static WebhookRequest signed(String payload, String secret, Instant at) {
        try {
            long timestamp = at.getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
            HttpHeaders headers = new HttpHeaders();
            headers.add("Stripe-Signature", "t=" + timestamp + ",v1=" + signature);
            return new WebhookRequest(payload, headers, Map.of());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
