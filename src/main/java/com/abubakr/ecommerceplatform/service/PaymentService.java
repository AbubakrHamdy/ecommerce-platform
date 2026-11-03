package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.payment.PaymentResponse;
import com.abubakr.ecommerceplatform.dto.payment.PaymentSessionResponse;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.InvalidWebhookException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.exception.ServiceUnavailableException;
import com.abubakr.ecommerceplatform.gateway.CheckoutSession;
import com.abubakr.ecommerceplatform.gateway.PaymentEvent;
import com.abubakr.ecommerceplatform.gateway.PaymentGateway;
import com.abubakr.ecommerceplatform.gateway.PaymentGateways;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.abubakr.ecommerceplatform.mapper.PaymentMapper;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import com.abubakr.ecommerceplatform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Don't hand a customer a session that would expire while they are typing their card details.
    private static final Duration MIN_REMAINING_TO_REUSE = Duration.ofMinutes(5);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateways gateways;
    private final StoreSettings storeSettings;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public PaymentSessionResponse startPayment(Long userId, Long orderId, PaymentProvider requested) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Only pending orders can be paid; this order is " + order.getStatus());
        }
        PaymentProvider provider = requested != null ? requested : storeSettings.paymentProviders().getFirst();
        if (!storeSettings.paymentProviders().contains(provider)) {
            throw new BadRequestException(provider + " is not available in this store");
        }
        PaymentGateway gateway = gateways.find(provider)
                .orElseThrow(() -> new ServiceUnavailableException(provider + " payments are not configured on this server"));

        List<Payment> open = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
        Optional<Payment> reusable = open.stream()
                .filter(payment -> payment.getProvider() == provider && stillUsable(payment))
                .findFirst();
        if (reusable.isPresent()) {
            return paymentMapper.toSessionResponse(reusable.get());
        }
        // Only one payable session per order, so a customer who switches providers cannot pay twice.
        open.forEach(this::closeSession);

        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .build());

        CheckoutSession session = gateway.createSession(order, payment);
        payment.setProviderSessionId(session.sessionId());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setExpiresAt(session.expiresAt());
        return paymentMapper.toSessionResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(Long userId, Long orderId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional
    public void handleWebhook(PaymentProvider provider, WebhookRequest request) {
        PaymentGateway gateway = gateways.find(provider)
                .orElseThrow(() -> new InvalidWebhookException(provider + " webhooks are not configured"));
        Optional<PaymentEvent> parsed = gateway.parseWebhook(request);
        if (parsed.isEmpty()) {
            return;
        }
        PaymentEvent event = parsed.get();

        if (paymentRepository.markEventProcessed(provider.name(), event.eventId()) == 0) {
            log.info("Ignoring duplicate {} event {}", provider, event.eventId());
            return;
        }

        Optional<Payment> found = paymentRepository.findByProviderAndProviderSessionId(provider, event.sessionId());
        if (found.isEmpty()) {
            log.warn("{} event {} refers to unknown session {}", provider, event.eventId(), event.sessionId());
            return;
        }
        Payment payment = found.get();
        switch (event.type()) {
            case SUCCEEDED -> recordSuccess(payment, event);
            case FAILED -> moveIfPending(payment, PaymentStatus.FAILED);
            case EXPIRED -> moveIfPending(payment, PaymentStatus.EXPIRED);
        }
    }

    // Called when an order is cancelled, so its payment pages stop accepting money.
    @Transactional
    public void closeOpenSessions(Long orderId) {
        paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING).forEach(this::closeSession);
    }

    private void recordSuccess(Payment payment, PaymentEvent event) {
        long expectedMinor = Money.toMinorUnits(payment.getAmount(), payment.getCurrency());
        if (event.amountMinor() != expectedMinor || !payment.getCurrency().equalsIgnoreCase(event.currency())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setReviewNote("Amount mismatch: expected %d %s, provider reported %d %s"
                    .formatted(expectedMinor, payment.getCurrency(), event.amountMinor(), event.currency()));
            log.error("Payment {}: {}. Order {} was NOT marked paid", payment.getId(), payment.getReviewNote(),
                    payment.getOrder().getId());
            return;
        }

        // Money was taken, so the payment is recorded as succeeded whatever its previous state was.
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTransactionId(event.transactionId());

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            events.publishEvent(OrderStatusChanged.to(order.getId(), OrderStatus.PAID));
        } else {
            payment.setReviewNote("Order was " + order.getStatus() + " when this payment succeeded; refund required");
            log.error("Payment {} succeeded but order {} is {}: refund required", payment.getId(), order.getId(),
                    order.getStatus());
        }
    }

    // Late or out-of-order events (e.g. "failed" after "succeeded") never move a payment backwards.
    private static void moveIfPending(Payment payment, PaymentStatus next) {
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(next);
        }
    }

    private void closeSession(Payment payment) {
        payment.setStatus(PaymentStatus.EXPIRED);
        gateways.find(payment.getProvider()).ifPresent(gateway -> gateway.expireSession(payment));
    }

    private static boolean stillUsable(Payment payment) {
        return payment.getExpiresAt() != null
                && payment.getExpiresAt().isAfter(Instant.now().plus(MIN_REMAINING_TO_REUSE));
    }
}
