package com.abubakr.ecommerceplatform.dto.stats;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuePoint(
        LocalDate date,
        BigDecimal revenue
) {}
