package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.OrderProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpiryJobTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @Test
    void cancelsOrdersOlderThanThePaymentWindow_andKeepsGoingIfOneWasChangedConcurrently() {
        OrderExpiryJob job = new OrderExpiryJob(orderRepository, orderService,
                new OrderProperties(Duration.ofMinutes(30), Duration.ofSeconds(60)));
        when(orderRepository.findIdsToExpire(any(), any())).thenReturn(List.of(1L, 2L, 3L));
        when(orderService.expireIfStillPending(1L)).thenReturn(true);
        when(orderService.expireIfStillPending(2L)).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 2L));
        when(orderService.expireIfStillPending(3L)).thenReturn(true);

        job.cancelUnpaidOrders();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findIdsToExpire(cutoff.capture(), now.capture());
        assertThat(cutoff.getValue()).isCloseTo(Instant.now().minus(Duration.ofMinutes(30)), within(Duration.ofSeconds(5)));
        assertThat(now.getValue()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        verify(orderService).expireIfStillPending(3L);
    }
}
