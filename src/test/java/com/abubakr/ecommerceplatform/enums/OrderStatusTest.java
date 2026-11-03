package com.abubakr.ecommerceplatform.enums;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.abubakr.ecommerceplatform.enums.OrderStatus.CANCELLED;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.DELIVERED;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PAID;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PENDING;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PROCESSING;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    @Test
    void everyTransitionMatchesTheAllowedFlow() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canMoveTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void shippedOrdersCanNoLongerBeCancelled() {
        assertThat(SHIPPED.canMoveTo(CANCELLED)).isFalse();
        assertThat(DELIVERED.canMoveTo(CANCELLED)).isFalse();
    }

    @Test
    void onlyPaidAndNotCancelledOrdersCountAsSales() {
        assertThat(EnumSet.allOf(OrderStatus.class).stream().filter(OrderStatus::isSale))
                .containsExactly(PAID, PROCESSING, SHIPPED, DELIVERED);
    }
}
