package com.abubakr.ecommerceplatform.gateway.paymob;

import com.abubakr.ecommerceplatform.config.PaymentProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import com.abubakr.ecommerceplatform.exception.InvalidWebhookException;
import com.abubakr.ecommerceplatform.exception.PaymentProviderException;
import com.abubakr.ecommerceplatform.gateway.CheckoutSession;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymobGatewayTest {

    private static final String HMAC_SECRET = "paymob_hmac_test";
    private static final String BASE_URL = "https://accept.paymob.com";

    private MockRestServiceServer paymobApi;
    private PaymobGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        paymobApi = MockRestServiceServer.bindTo(builder).build();
        gateway = new PaymobGateway(
                new PaymobProperties(BASE_URL, "sk_test", "pk_test", HMAC_SECRET, List.of(4567), "https://api.shop/webhooks/paymob"),
                new PaymentProperties(Duration.ofMinutes(31), "http://shop/orders/{orderId}?ok", "http://shop/orders/{orderId}?no"),
                JsonMapper.builder().build(),
                builder.build());
    }

    @Test
    void createSession_sendsTheOrderTotalInPiastersAndBuildsTheUnifiedCheckoutUrl() {
        paymobApi.expect(requestTo(BASE_URL + "/v1/intention/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Token sk_test"))
                .andExpect(jsonPath("$.amount").value(5997))
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.payment_methods[0]").value(4567))
                .andExpect(jsonPath("$.special_reference").value("payment-11"))
                .andExpect(jsonPath("$.billing_data.email").value("jane@example.com"))
                .andExpect(jsonPath("$.redirection_url").value("http://shop/orders/7?ok"))
                .andRespond(withSuccess("""
                        {"id":"pi_test_abc","client_secret":"egy_csk_test_xyz","intention_order_id":98765,"status":"intended"}""",
                        MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.createSession(order(), payment());

        paymobApi.verify();
        assertThat(session.sessionId()).isEqualTo("98765");
        assertThat(session.checkoutUrl())
                .isEqualTo(BASE_URL + "/unifiedcheckout/?publicKey=pk_test&clientSecret=egy_csk_test_xyz");
    }

    @Test
    void createSession_whenPaymobIsDown_reportsAProviderError() {
        paymobApi.expect(requestTo(BASE_URL + "/v1/intention/")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createSession(order(), payment()))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void successfulTransactionCallback_withValidHmac_becomesASucceededEvent() {
        Optional<PaymentEvent> event = gateway.parseWebhook(callback(transaction(true, false, 5997), validHmac(true, false, 5997)));

        assertThat(event).contains(new PaymentEvent("123456:SUCCEEDED", PaymentEvent.Type.SUCCEEDED, "98765", "123456", 5997, "EGP"));
    }

    @Test
    void declinedCard_becomesAFailedEvent() {
        Optional<PaymentEvent> event = gateway.parseWebhook(callback(transaction(false, false, 5997), validHmac(false, false, 5997)));

        assertThat(event).get().extracting(PaymentEvent::type).isEqualTo(PaymentEvent.Type.FAILED);
    }

    @Test
    void pendingTransaction_isIgnoredUntilItSettles() {
        assertThat(gateway.parseWebhook(callback(transaction(false, true, 5997), validHmac(false, true, 5997)))).isEmpty();
    }

    @Test
    void amountChangedAfterSigning_isRejected() {
        WebhookRequest tampered = callback(transaction(true, false, 100), validHmac(true, false, 5997));

        assertThatThrownBy(() -> gateway.parseWebhook(tampered))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("Invalid Paymob signature");
    }

    @Test
    void missingHmac_isRejected() {
        assertThatThrownBy(() -> gateway.parseWebhook(
                new WebhookRequest(transaction(true, false, 5997), new HttpHeaders(), Map.of())))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void otherCallbackTypes_areIgnored() {
        WebhookRequest tokenCallback = new WebhookRequest("""
                {"type":"TOKEN","obj":{"token":"abc"}}""", new HttpHeaders(), Map.of("hmac", "whatever"));

        assertThat(gateway.parseWebhook(tokenCallback)).isEmpty();
    }

    @Test
    void withoutCredentials_paymobIsNotOffered() {
        PaymobGateway unconfigured = new PaymobGateway(
                new PaymobProperties(BASE_URL, "", "", "", List.of(), ""),
                new PaymentProperties(Duration.ofMinutes(31), "x", "x"), JsonMapper.builder().build(), RestClient.create());

        assertThat(unconfigured.isConfigured()).isFalse();
    }

    // Written out by hand in Paymob's documented field order, independently of the code under test.
    private static String validHmac(boolean success, boolean pending, long amountCents) {
        String concatenated = amountCents        // amount_cents
                + "2026-11-02T10:00:00.000000"    // created_at
                + "EGP"                           // currency
                + "false"                         // error_occured
                + "false"                         // has_parent_transaction
                + "123456"                        // id
                + "4567"                          // integration_id
                + "true"                          // is_3d_secure
                + "false"                         // is_auth
                + "false"                         // is_capture
                + "false"                         // is_refunded
                + "true"                          // is_standalone_payment
                + "false"                         // is_voided
                + "98765"                         // order.id
                + "302"                           // owner
                + pending                         // pending
                + "512345xxxxxx2346"              // source_data.pan
                + "MasterCard"                    // source_data.sub_type
                + "card"                          // source_data.type
                + success;                        // success
        return PaymobGateway.hmacSha512(HMAC_SECRET, concatenated);
    }

    private static String transaction(boolean success, boolean pending, long amountCents) {
        return """
                {"type":"TRANSACTION","obj":{
                  "id":123456,"pending":%s,"amount_cents":%d,"success":%s,"is_auth":false,"is_capture":false,
                  "is_standalone_payment":true,"is_voided":false,"is_refunded":false,"is_3d_secure":true,
                  "integration_id":4567,"has_parent_transaction":false,"error_occured":false,"owner":302,
                  "created_at":"2026-11-02T10:00:00.000000","currency":"EGP",
                  "order":{"id":98765,"merchant_order_id":"payment-11"},
                  "source_data":{"pan":"512345xxxxxx2346","type":"card","sub_type":"MasterCard"}}}"""
                .formatted(pending, amountCents, success);
    }

    private static WebhookRequest callback(String body, String hmac) {
        return new WebhookRequest(body, new HttpHeaders(), Map.of("hmac", hmac));
    }

    private static Order order() {
        User user = User.builder().firstName("Jane").lastName("Customer").email("jane@example.com").build();
        Order order = Order.builder()
                .user(user).totalAmount(new BigDecimal("59.97")).currency("EGP")
                .shippingAddress(new ShippingAddress("9 Nile Corniche", "Giza", null, "12511", "Egypt"))
                .build();
        order.setId(7L);
        order.addItem(OrderItem.builder().productName("Wireless Mouse").priceAtPurchase(new BigDecimal("19.99")).quantity(3).build());
        return order;
    }

    private static Payment payment() {
        Payment payment = Payment.builder().provider(PaymentProvider.PAYMOB).status(PaymentStatus.PENDING)
                .amount(new BigDecimal("59.97")).currency("EGP").build();
        payment.setId(11L);
        return payment;
    }
}
