package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

// An inclusive range of calendar days in the store's time zone, turned into the exact instants to query.
// In Cairo, 2 March starts at 1 March 22:00 UTC, so a late-evening order lands on the day the admin expects.
record ReportPeriod(LocalDate from, LocalDate to, Instant start, Instant end) {

    static final int DEFAULT_DAYS = 30;

    static ReportPeriod of(LocalDate from, LocalDate to, ZoneId zone, long maxDays) {
        LocalDate resolvedTo = to != null ? to
                : from != null ? from.plusDays(DEFAULT_DAYS - 1)
                : LocalDate.now(zone);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_DAYS - 1);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BadRequestException("'from' (" + resolvedFrom + ") must not be after 'to' (" + resolvedTo + ")");
        }
        long days = days(resolvedFrom, resolvedTo);
        if (days > maxDays) {
            throw new BadRequestException("The period is " + days + " days; at most " + maxDays + " are allowed here");
        }
        return new ReportPeriod(resolvedFrom, resolvedTo,
                resolvedFrom.atStartOfDay(zone).toInstant(),
                resolvedTo.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static long days(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
