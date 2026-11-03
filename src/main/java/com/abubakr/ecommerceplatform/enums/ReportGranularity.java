package com.abubakr.ecommerceplatform.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// How a revenue chart groups days. Weeks start on Monday, matching PostgreSQL's date_trunc('week', ...).
// A daily chart is capped at about a year; longer ranges should be viewed by week or month.
public enum ReportGranularity {
    DAY("day", 366),
    WEEK("week", 5 * 366),
    MONTH("month", 5 * 366);

    private final String sqlUnit;
    private final long maxDays;

    ReportGranularity(String sqlUnit, long maxDays) {
        this.sqlUnit = sqlUnit;
        this.maxDays = maxDays;
    }

    public String sqlUnit() {
        return sqlUnit;
    }

    public long maxDays() {
        return maxDays;
    }

    public LocalDate bucketStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    public LocalDate nextBucket(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }
}
