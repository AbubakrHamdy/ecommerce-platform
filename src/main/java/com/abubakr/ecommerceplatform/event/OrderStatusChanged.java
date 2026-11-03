package com.abubakr.ecommerceplatform.event;

import com.abubakr.ecommerceplatform.enums.CancellationReason;
import com.abubakr.ecommerceplatform.enums.OrderStatus;

// Published only when an order's status really changes; listeners react after the transaction commits.
public record OrderStatusChanged(
        Long orderId,
        OrderStatus status,
        CancellationReason cancellationReason
) {

    public static OrderStatusChanged to(Long orderId, OrderStatus status) {
        return new OrderStatusChanged(orderId, status, null);
    }

    public static OrderStatusChanged cancelled(Long orderId, CancellationReason reason) {
        return new OrderStatusChanged(orderId, OrderStatus.CANCELLED, reason);
    }
}
