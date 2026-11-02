package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Real PostgreSQL: verifies the JPQL "not exists" rule, which a mock could not.
@SpringBootTest
@ActiveProfiles("dev")
class OrderExpiryQueryIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> createdOrders = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdOrders.forEach(id -> {
            jdbcTemplate.update("delete from payments where order_id = ?", id);
            jdbcTemplate.update("delete from orders where id = ?", id);
        });
    }

    @Test
    void oldUnpaidOrders_expire_unlessTheCustomerStillHasAnOpenPaymentPage() {
        Instant now = Instant.now();
        Long abandoned = pendingOrderCreatedAgo(Duration.ofMinutes(40));
        Long payingRightNow = pendingOrderCreatedAgo(Duration.ofMinutes(40));
        Long sessionAlreadyExpired = pendingOrderCreatedAgo(Duration.ofMinutes(40));
        Long recent = pendingOrderCreatedAgo(Duration.ofMinutes(5));
        addPayment(payingRightNow, PaymentStatus.PENDING, now.plus(Duration.ofMinutes(10)));
        addPayment(sessionAlreadyExpired, PaymentStatus.PENDING, now.minus(Duration.ofMinutes(1)));

        List<Long> toExpire = orderRepository.findIdsToExpire(now.minus(Duration.ofMinutes(30)), now);

        assertThat(toExpire).contains(abandoned, sessionAlreadyExpired).doesNotContain(payingRightNow, recent);
    }

    private Long pendingOrderCreatedAgo(Duration age) {
        Long id = orderRepository.save(Order.builder()
                .user(userRepository.findByEmail("customer@example.com").orElseThrow())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN)
                .currency("EGP")
                .shippingAddress(new ShippingAddress("1 Test St", "Cairo", null, "11511", "Egypt"))
                .build()).getId();
        // Relative to the stored value: Hibernate writes Instants as UTC, a JDBC Timestamp would use the local zone.
        jdbcTemplate.update("update orders set created_at = created_at - make_interval(secs => ?) where id = ?",
                age.toSeconds(), id);
        createdOrders.add(id);
        return id;
    }

    private void addPayment(Long orderId, PaymentStatus status, Instant expiresAt) {
        paymentRepository.save(Payment.builder()
                .order(orderRepository.getReferenceById(orderId))
                .provider(PaymentProvider.STRIPE)
                .status(status)
                .amount(BigDecimal.TEN)
                .currency("EGP")
                .providerSessionId("cs_it_" + orderId)
                .expiresAt(expiresAt)
                .build());
    }
}
