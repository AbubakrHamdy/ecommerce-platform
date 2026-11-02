package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
class OrderOptimisticLockingIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentStatusChanges_theSecondFailsInsteadOfOverwriting() {
        Long orderId = orderRepository.save(Order.builder()
                .user(userRepository.findByEmail("customer@example.com").orElseThrow())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN)
                .shippingAddress(new ShippingAddress("1 Test St", "Cairo", null, "11511", "Egypt"))
                .build()).getId();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            // The outer transaction reads the order, then another transaction pays it and commits first.
            assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                Order staleCopy = orderRepository.findById(orderId).orElseThrow();
                inner.executeWithoutResult(s -> orderRepository.findById(orderId).orElseThrow().setStatus(OrderStatus.PAID));
                staleCopy.setStatus(OrderStatus.CANCELLED);
            })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        } finally {
            orderRepository.deleteById(orderId);
        }
    }
}
