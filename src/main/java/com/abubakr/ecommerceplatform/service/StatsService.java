package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.stats.RevenuePoint;
import com.abubakr.ecommerceplatform.dto.stats.RevenueSeriesResponse;
import com.abubakr.ecommerceplatform.dto.stats.SalesSummaryResponse;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.enums.ReportGranularity;
import com.abubakr.ecommerceplatform.repository.SalesReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Reports cover the store's current currency only; orders placed before a currency change are not mixed in.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    static final long MAX_SUMMARY_DAYS = 5 * 366;
    private static final List<String> SALE_STATUSES = Arrays.stream(OrderStatus.values())
            .filter(OrderStatus::isSale).map(Enum::name).toList();

    private final SalesReportRepository salesReportRepository;
    private final StoreSettings storeSettings;

    public SalesSummaryResponse summary(LocalDate from, LocalDate to) {
        ReportPeriod period = ReportPeriod.of(from, to, storeSettings.timeZone(), MAX_SUMMARY_DAYS);
        String currency = storeSettings.currencyCode();

        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status, 0L);
        }
        BigDecimal revenue = BigDecimal.ZERO;
        long paidOrders = 0;
        for (SalesReportRepository.StatusTotal total : salesReportRepository.totalsByStatus(currency, period.start(), period.end())) {
            ordersByStatus.put(total.getStatus(), total.getOrders());
            if (total.getStatus().isSale()) {
                revenue = revenue.add(total.getAmount());
                paidOrders += total.getOrders();
            }
        }

        int scale = fractionDigits(currency);
        BigDecimal average = paidOrders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paidOrders), scale, RoundingMode.HALF_UP);
        return new SalesSummaryResponse(period.from(), period.to(), currency,
                revenue.setScale(scale, RoundingMode.HALF_UP), paidOrders, average.setScale(scale, RoundingMode.HALF_UP),
                ordersByStatus);
    }

    public RevenueSeriesResponse revenue(LocalDate from, LocalDate to, ReportGranularity granularity) {
        ReportPeriod period = ReportPeriod.of(from, to, storeSettings.timeZone(), granularity.maxDays());
        String currency = storeSettings.currencyCode();
        int scale = fractionDigits(currency);

        Map<LocalDate, SalesReportRepository.BucketTotal> totals = new HashMap<>();
        salesReportRepository.salesByBucket(granularity.sqlUnit(), storeSettings.timeZone().getId(), currency,
                        SALE_STATUSES, utc(period.start()), utc(period.end()))
                .forEach(total -> totals.put(total.getBucket(), total));

        List<RevenuePoint> points = new ArrayList<>();
        for (LocalDate bucket = granularity.bucketStart(period.from()); !bucket.isAfter(period.to());
             bucket = granularity.nextBucket(bucket)) {
            SalesReportRepository.BucketTotal total = totals.get(bucket);
            points.add(total == null
                    ? new RevenuePoint(bucket, 0, BigDecimal.ZERO.setScale(scale))
                    : new RevenuePoint(bucket, total.getOrders(), total.getAmount().setScale(scale, RoundingMode.HALF_UP)));
        }
        return new RevenueSeriesResponse(period.from(), period.to(), currency, granularity, points);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static int fractionDigits(String currency) {
        return Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
    }
}
