package com.abubakr.ecommerceplatform.dto.stats;

import java.math.BigDecimal;
import java.time.LocalDate;

// date is the first day of the bucket (the day, the Monday of the week, or the 1st of the month).
public record RevenuePoint(
        LocalDate date,
        long paidOrders,
        BigDecimal revenue
) {}
