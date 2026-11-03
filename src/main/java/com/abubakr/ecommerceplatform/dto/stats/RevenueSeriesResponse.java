package com.abubakr.ecommerceplatform.dto.stats;

import com.abubakr.ecommerceplatform.enums.ReportGranularity;

import java.time.LocalDate;
import java.util.List;

// One point per bucket, including empty ones, so a chart can plot the list as is. The first and last buckets
// only count the days inside from..to, even when they start before from (a week or month cut by the range).
public record RevenueSeriesResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        ReportGranularity granularity,
        List<RevenuePoint> points
) {}
