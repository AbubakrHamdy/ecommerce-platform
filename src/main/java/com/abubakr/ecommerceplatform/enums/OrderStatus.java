package com.abubakr.ecommerceplatform.enums;

public enum OrderStatus {
    PENDING,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    // The customer has paid and the order was not cancelled: this is what reports count as revenue.
    public boolean isSale() {
        return this == PAID || this == PROCESSING || this == SHIPPED || this == DELIVERED;
    }
}
