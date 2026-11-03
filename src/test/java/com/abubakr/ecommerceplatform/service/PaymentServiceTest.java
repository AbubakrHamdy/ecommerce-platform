package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.StoreProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import com.abubakr.ecommerceplatform.event.PaymentNeedsReview;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ServiceUnavailableException;
import com.abubakr.ecommerceplatform.gateway.CheckoutSession;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.PaymentGateway;
import com.abubakr.ecommerceplatform.gateway.PaymentGateways;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.abubakr.ecommerceplatform.mapper.PaymentMapperImpl;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import com.abubakr.ecommerceplatform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.abubakr.ecommerceplatform.enums.PaymentProvider.PAYMOB;
import static com.abubakr.ecommerceplatform.enums.PaymentProvider.STRIPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long USER_ID = 2L;
    private static final WebhookRequest REQUEST = new WebhookRequest("{}", new HttpHeaders(), Map.of());

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateways gateways;
    @Mock private PaymentGateway stripe;
    @Mock private PaymentGateway paymob;
    @Mock private ApplicationEventPublisher events;

    private PaymentService service;
    private Order order;

    @BeforeEach
    void setUp() {
        StoreSettings bothProviders = new StoreSettings(new StoreProperties("AE", "EGP", List.of(STRIPE, PAYMOB)));
        service = new PaymentService(orderRepository, paymentRepository, gateways, bothProviders, new PaymentMapperImpl(), events);
        order = Order.builder().status(OrderStatus.PENDING).totalAmount(new BigDecimal("59.97")).currency("EGP").build();
        order.setId(7L);
    }

    @Test
    void startPayment_createsASessionForTheOrderTotal() {
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(stripe.createSession(any(), any())).thenReturn(
                new CheckoutSession("cs_1", "https://checkout.stripe.com/cs_1", Instant.now().plus(Duration.ofMinutes(31))));

        var response = service.startPayment(USER_ID, 7L, STRIPE);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("59.97");
        assertThat(saved.getValue().getCurrency()).isEqualTo("EGP");
        assertThat(saved.getValue().getProviderSessionId()).isEqualTo("cs_1");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/cs_1");
    }

    @Test
    void startPayment_againForSameProvider_reusesTheOpenSession() {
        Payment open = payment(STRIPE, PaymentStatus.PENDING, Instant.now().plus(Duration.ofMinutes(20)));
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(paymentRepository.findByOrderIdAndStatus(7L, PaymentStatus.PENDING)).thenReturn(List.of(open));

        assertThat(service.startPayment(USER_ID, 7L, STRIPE).paymentId()).isEqualTo(open.getId());
        verify(stripe, never()).createSession(any(), any());
    }

    @Test
    void startPayment_switchingProvider_closesTheOtherSessionSoTheOrderCantBePaidTwice() {
        Payment stripeSession = payment(STRIPE, PaymentStatus.PENDING, Instant.now().plus(Duration.ofMinutes(20)));
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));
        when(gateways.find(PAYMOB)).thenReturn(Optional.of(paymob));
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(paymentRepository.findByOrderIdAndStatus(7L, PaymentStatus.PENDING)).thenReturn(List.of(stripeSession));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> withId(inv.getArgument(0), 2L));
        when(paymob.createSession(any(), any())).thenReturn(new CheckoutSession("123", "https://paymob/x", Instant.now()));

        service.startPayment(USER_ID, 7L, PAYMOB);

        assertThat(stripeSession.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(stripe).expireSession(stripeSession);
    }

    @Test
    void startPayment_rejectsOrdersThatAreNotPending_andProvidersTheStoreDoesntOffer() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.startPayment(USER_ID, 7L, STRIPE)).isInstanceOf(ConflictException.class);

        PaymentService egyptOnly = new PaymentService(orderRepository, paymentRepository, gateways,
                new StoreSettings(new StoreProperties("EG", null, null)), new PaymentMapperImpl(), events);
        order.setStatus(OrderStatus.PENDING);
        assertThatThrownBy(() -> egyptOnly.startPayment(USER_ID, 7L, STRIPE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STRIPE is not available in this store");
    }

    @Test
    void startPayment_providerWithoutCredentials_isUnavailable() {
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));
        when(gateways.find(STRIPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPayment(USER_ID, 7L, STRIPE))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void webhook_success_marksPaymentSucceededAndOrderPaid() {
        Payment payment = givenWebhook(event(PaymentEvent.Type.SUCCEEDED, 5997, "egp"), true);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getTransactionId()).isEqualTo("pi_1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(events).publishEvent(OrderStatusChanged.to(7L, OrderStatus.PAID));
        verify(events, never()).publishEvent(any(PaymentNeedsReview.class));
    }

    @Test
    void webhook_duplicateDelivery_isIgnored() {
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(stripe.parseWebhook(REQUEST)).thenReturn(Optional.of(event(PaymentEvent.Type.SUCCEEDED, 5997, "egp")));
        when(paymentRepository.markEventProcessed("STRIPE", "evt_1")).thenReturn(0);

        service.handleWebhook(STRIPE, REQUEST);

        verify(paymentRepository, never()).findByProviderAndProviderSessionId(any(), anyString());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(events);
    }

    @Test
    void webhook_wrongAmount_doesNotMarkTheOrderPaid() {
        Payment payment = givenWebhook(event(PaymentEvent.Type.SUCCEEDED, 100, "egp"), true);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getReviewNote()).startsWith("Amount mismatch");
        verify(events, never()).publishEvent(any(OrderStatusChanged.class));
        verify(events).publishEvent(new PaymentNeedsReview(1L));
    }

    @Test
    void webhook_wrongCurrency_doesNotMarkTheOrderPaid() {
        givenWebhook(event(PaymentEvent.Type.SUCCEEDED, 5997, "usd"), true);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void webhook_paymentForAnAlreadyCancelledOrder_isRecordedAndFlaggedForRefund() {
        order.setStatus(OrderStatus.CANCELLED);
        Payment payment = givenWebhook(event(PaymentEvent.Type.SUCCEEDED, 5997, "EGP"), true);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getReviewNote()).contains("refund required");
        verify(events, never()).publishEvent(any(OrderStatusChanged.class));
        verify(events).publishEvent(new PaymentNeedsReview(1L));
    }

    @Test
    void webhook_secondSuccessEventForTheSameCharge_isNotTreatedAsADoublePayment() {
        Payment payment = givenWebhook(new PaymentEvent("evt_2", PaymentEvent.Type.SUCCEEDED, "cs_1", "pi_1", 5997, "egp"), true);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTransactionId("pi_1");
        order.setStatus(OrderStatus.PAID);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(payment.getReviewNote()).isNull();
        verifyNoInteractions(events);
    }

    @Test
    void webhook_secondSuccessWithADifferentCharge_isFlaggedForRefund() {
        Payment payment = givenWebhook(new PaymentEvent("evt_3", PaymentEvent.Type.SUCCEEDED, "cs_1", "txn_second", 5997, "egp"), true);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTransactionId("pi_1");
        order.setStatus(OrderStatus.PAID);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(payment.getReviewNote()).contains("refund required");
        verify(events).publishEvent(new PaymentNeedsReview(1L));
    }

    @Test
    void webhook_failedArrivingAfterSuccess_neverMovesThePaymentBackwards() {
        Payment payment = givenWebhook(event(PaymentEvent.Type.FAILED, 5997, "egp"), true);
        payment.setStatus(PaymentStatus.SUCCEEDED);

        service.handleWebhook(STRIPE, REQUEST);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void webhook_eventTypeWeDontUse_isAcknowledgedWithoutSideEffects() {
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(stripe.parseWebhook(REQUEST)).thenReturn(Optional.empty());

        service.handleWebhook(STRIPE, REQUEST);

        verify(paymentRepository, never()).markEventProcessed(anyString(), anyString());
    }

    @Test
    void closeOpenSessions_whenTheOrderIsCancelled_stopsThePaymentPageAcceptingMoney() {
        Payment open = payment(STRIPE, PaymentStatus.PENDING, Instant.now().plus(Duration.ofMinutes(20)));
        when(paymentRepository.findByOrderIdAndStatus(7L, PaymentStatus.PENDING)).thenReturn(List.of(open));
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));

        service.closeOpenSessions(7L);

        assertThat(open.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(stripe).expireSession(open);
    }

    private Payment givenWebhook(PaymentEvent event, boolean firstDelivery) {
        Payment payment = payment(STRIPE, PaymentStatus.PENDING, Instant.now().plus(Duration.ofMinutes(20)));
        when(gateways.find(STRIPE)).thenReturn(Optional.of(stripe));
        when(stripe.parseWebhook(REQUEST)).thenReturn(Optional.of(event));
        when(paymentRepository.markEventProcessed("STRIPE", event.eventId())).thenReturn(firstDelivery ? 1 : 0);
        when(paymentRepository.findByProviderAndProviderSessionId(STRIPE, "cs_1")).thenReturn(Optional.of(payment));
        return payment;
    }

    private static PaymentEvent event(PaymentEvent.Type type, long amountMinor, String currency) {
        return new PaymentEvent("evt_1", type, "cs_1", "pi_1", amountMinor, currency);
    }

    private Payment payment(PaymentProvider provider, PaymentStatus status, Instant expiresAt) {
        Payment payment = Payment.builder()
                .order(order).provider(provider).status(status)
                .amount(order.getTotalAmount()).currency(order.getCurrency())
                .providerSessionId("cs_1").checkoutUrl("https://pay/cs_1").expiresAt(expiresAt)
                .build();
        payment.setId(1L);
        order.setPayments(new ArrayList<>(List.of(payment)));
        return payment;
    }

    private static Payment withId(Payment payment, Long id) {
        payment.setId(id);
        return payment;
    }
}
