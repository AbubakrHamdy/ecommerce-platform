package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.stats.RevenueSeriesResponse;
import com.abubakr.ecommerceplatform.dto.stats.SalesSummaryResponse;
import com.abubakr.ecommerceplatform.dto.stats.TopProductsResponse;
import com.abubakr.ecommerceplatform.enums.ReportGranularity;
import com.abubakr.ecommerceplatform.enums.TopProductRanking;
import com.abubakr.ecommerceplatform.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Stats", description = "Sales reports. Days are calendar days in the store time zone; "
        + "from and to are inclusive and default to the last 30 days.")
public class AdminStatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    @Operation(summary = "Revenue, paid orders, average order value and order counts by status",
            description = "Revenue counts PAID, PROCESSING, SHIPPED and DELIVERED orders in the store currency.")
    public SalesSummaryResponse summary(
            @Parameter(description = "First day, e.g. 2026-10-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Last day, e.g. 2026-10-31") @RequestParam(required = false) LocalDate to) {
        return statsService.summary(from, to);
    }

    @GetMapping("/revenue")
    @Operation(summary = "Revenue and paid orders per day, week (from Monday) or month, empty buckets included",
            description = "DAY covers up to 366 days, WEEK and MONTH up to 5 years.")
    public RevenueSeriesResponse revenue(
            @Parameter(description = "First day, e.g. 2026-10-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Last day, e.g. 2026-10-31") @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {
        return statsService.revenue(from, to, granularity);
    }

    @GetMapping("/top-products")
    @Operation(summary = "Best-selling products by revenue or by units sold",
            description = "limit is 1 to 50. Only paid orders in the store currency count.")
    public TopProductsResponse topProducts(
            @Parameter(description = "First day, e.g. 2026-10-01") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Last day, e.g. 2026-10-31") @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "REVENUE") TopProductRanking by) {
        return statsService.topProducts(from, to, limit, by);
    }
}
