package com.abubakr.ecommerceplatform.dto.stats;

import java.math.BigDecimal;
import java.util.List;

public record SalesStatsResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        long totalProducts,
        List<RevenuePoint> revenueOverTime
) {}
