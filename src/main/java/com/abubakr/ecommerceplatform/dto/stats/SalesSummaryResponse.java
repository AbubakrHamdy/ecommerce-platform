package com.abubakr.ecommerceplatform.dto.stats;

import com.abubakr.ecommerceplatform.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// revenue, paidOrders and averageOrderValue count sales only (PAID, PROCESSING, SHIPPED, DELIVERED);
// ordersByStatus lists every status, including PENDING and CANCELLED, so the admin sees the whole funnel.
public record SalesSummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal revenue,
        long paidOrders,
        BigDecimal averageOrderValue,
        Map<OrderStatus, Long> ordersByStatus
) {}
