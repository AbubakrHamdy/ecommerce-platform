package com.abubakr.ecommerceplatform.gateway.paymob;

import com.abubakr.ecommerceplatform.config.PaymentProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.exception.InvalidWebhookException;
import com.abubakr.ecommerceplatform.exception.PaymentProviderException;
import com.abubakr.ecommerceplatform.gateway.CheckoutSession;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.PaymentGateway;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.abubakr.ecommerceplatform.service.Money;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Paymob Intention API (unified checkout). Paymob has no official Java SDK, so this is plain HTTP.
@Component
public class PaymobGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymobGateway.class);
    private static final String UNKNOWN = "NA";

    // Paymob's documented order of fields for the transaction-callback HMAC (alphabetical by key).
    static final List<String> HMAC_FIELDS = List.of(
            "amount_cents", "created_at", "currency", "error_occured", "has_parent_transaction", "id",
            "integration_id", "is_3d_secure", "is_auth", "is_capture", "is_refunded", "is_standalone_payment",
            "is_voided", "order.id", "owner", "pending", "source_data.pan", "source_data.sub_type",
            "source_data.type", "success");

    private final PaymobProperties paymob;
    private final PaymentProperties payments;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    @Autowired
    public PaymobGateway(PaymobProperties paymob, PaymentProperties payments, JsonMapper jsonMapper,
                         RestClient.Builder restClientBuilder) {
        this(paymob, payments, jsonMapper, restClientBuilder.clone()
                .baseUrl(paymob.baseUrl())
                .requestFactory(timeoutRequestFactory())
                .build());
    }

    PaymobGateway(PaymobProperties paymob, PaymentProperties payments, JsonMapper jsonMapper, RestClient restClient) {
        this.paymob = paymob;
        this.payments = payments;
        this.jsonMapper = jsonMapper;
        this.restClient = restClient;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYMOB;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(paymob.secretKey()) && StringUtils.hasText(paymob.publicKey())
                && StringUtils.hasText(paymob.hmacSecret())
                && paymob.integrationIds() != null && !paymob.integrationIds().isEmpty();
    }

    @Override
    public CheckoutSession createSession(Order order, Payment payment) {
        long amountCents = Money.toMinorUnits(payment.getAmount(), payment.getCurrency());
        // A single line for the whole order: Paymob rejects intentions whose item amounts don't add up exactly.
        IntentionRequest request = new IntentionRequest(
                amountCents,
                payment.getCurrency(),
                paymob.integrationIds(),
                List.of(new Item("Order #" + order.getId(), amountCents, order.getItems().size() + " item(s)", 1)),
                billingData(order),
                "payment-" + payment.getId(),
                paymob.notificationUrl(),
                payments.successUrlFor(order.getId()));

        IntentionResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/intention/")
                    .header("Authorization", "Token " + paymob.secretKey())
                    .body(request)
                    .retrieve()
                    .body(IntentionResponse.class);
        } catch (RestClientException e) {
            throw new PaymentProviderException("Paymob could not create a payment intention", e);
        }
        if (response == null || !StringUtils.hasText(response.clientSecret()) || response.intentionOrderId() == null) {
            throw new PaymentProviderException("Paymob returned an incomplete intention", null);
        }

        String checkoutUrl = UriComponentsBuilder.fromUriString(paymob.baseUrl())
                .path("/unifiedcheckout/")
                .queryParam("publicKey", paymob.publicKey())
                .queryParam("clientSecret", response.clientSecret())
                .encode()
                .toUriString();
        return new CheckoutSession(String.valueOf(response.intentionOrderId()), checkoutUrl,
                Instant.now().plus(payments.sessionTtl()));
    }

    @Override
    public Optional<PaymentEvent> parseWebhook(WebhookRequest request) {
        String receivedHmac = request.queryParams().get("hmac");
        if (!StringUtils.hasText(receivedHmac)) {
            throw new InvalidWebhookException("Missing Paymob hmac");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(request.body());
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Malformed Paymob callback", e);
        }
        if (!"TRANSACTION".equals(root.path("type").asString())) {
            return Optional.empty();
        }
        JsonNode transaction = root.path("obj");
        String expected = hmacSha512(paymob.hmacSecret(), concatenatedHmacFields(transaction));
        // Constant-time comparison, so response timing reveals nothing about how much of a forged hmac matched.
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                receivedHmac.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidWebhookException("Invalid Paymob signature");
        }

        if (transaction.path("pending").asBoolean()) {
            return Optional.empty();
        }
        boolean success = transaction.path("success").asBoolean();
        boolean reversed = transaction.path("is_voided").asBoolean() || transaction.path("is_refunded").asBoolean();
        if (success && reversed) {
            return Optional.empty();
        }
        PaymentEvent.Type type = success ? PaymentEvent.Type.SUCCEEDED : PaymentEvent.Type.FAILED;
        String transactionId = transaction.path("id").asString();
        return Optional.of(new PaymentEvent(
                transactionId + ":" + type,
                type,
                transaction.path("order").path("id").asString(),
                transactionId,
                transaction.path("amount_cents").asLong(),
                transaction.path("currency").asString()));
    }

    // Paymob has no endpoint to cancel an intention; a late payment is caught by the order checks instead.
    @Override
    public void expireSession(Payment payment) {
        log.debug("Paymob intention {} cannot be cancelled remotely; it will simply expire", payment.getProviderSessionId());
    }

    static String concatenatedHmacFields(JsonNode transaction) {
        StringBuilder concatenated = new StringBuilder();
        for (String field : HMAC_FIELDS) {
            JsonNode node = transaction;
            for (String part : field.split("\\.")) {
                node = node.path(part);
            }
            if (!node.isMissingNode()) {
                concatenated.append(node.asString());
            }
        }
        return concatenated.toString();
    }

    static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 is not available", e);
        }
    }

    // Paymob requires every billing field; values we don't collect (phone, building, ...) are sent as "NA".
    private static BillingData billingData(Order order) {
        User user = order.getUser();
        ShippingAddress address = order.getShippingAddress();
        return new BillingData(user.getFirstName(), user.getLastName(), user.getEmail(), UNKNOWN,
                address.street(), UNKNOWN, UNKNOWN, UNKNOWN, address.city(),
                address.state() == null ? UNKNOWN : address.state(), address.country(), address.postalCode());
    }

    private static JdkClientHttpRequestFactory timeoutRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    record IntentionRequest(
            long amount,
            String currency,
            @JsonProperty("payment_methods") List<Integer> paymentMethods,
            List<Item> items,
            @JsonProperty("billing_data") BillingData billingData,
            @JsonProperty("special_reference") String specialReference,
            @JsonProperty("notification_url") String notificationUrl,
            @JsonProperty("redirection_url") String redirectionUrl) {
    }

    record Item(String name, long amount, String description, int quantity) {
    }

    record BillingData(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            @JsonProperty("phone_number") String phoneNumber,
            String street,
            String building,
            String floor,
            String apartment,
            String city,
            String state,
            String country,
            @JsonProperty("postal_code") String postalCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntentionResponse(
            String id,
            @JsonProperty("client_secret") String clientSecret,
            @JsonProperty("intention_order_id") Long intentionOrderId) {
    }
}
