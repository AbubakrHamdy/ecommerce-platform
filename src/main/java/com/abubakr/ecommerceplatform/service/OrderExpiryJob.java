package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.OrderProperties;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// Cancels checkouts that were never paid so their reserved stock goes back on sale.
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderProperties properties;

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval}")
    public void cancelUnpaidOrders() {
        Instant cutoff = Instant.now().minus(properties.paymentTimeout());
        List<Long> candidates = orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PENDING, cutoff);

        int cancelled = 0;
        for (Long orderId : candidates) {
            try {
                if (orderService.expireIfStillPending(orderId)) {
                    cancelled++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // Changed at the same moment (e.g. a payment just arrived); the other change wins.
                log.info("Skipped expiring order {}: it was modified concurrently", orderId);
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid order(s) older than {}", cancelled, properties.paymentTimeout());
        }
    }
}
