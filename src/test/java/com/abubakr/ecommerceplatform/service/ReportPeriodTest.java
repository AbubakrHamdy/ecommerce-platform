package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPeriodTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Test
    void daysStartAtMidnightInTheStoreTimeZone_andTheLastDayIsIncluded() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 2), CAIRO, 31);

        assertThat(period.start()).isEqualTo(Instant.parse("2020-02-29T22:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2020-03-02T22:00:00Z"));
    }

    @Test
    void missingEnds_defaultToThirtyDays() {
        LocalDate today = LocalDate.now(CAIRO);

        ReportPeriod none = ReportPeriod.of(null, null, CAIRO, 31);
        assertThat(none.to()).isEqualTo(today);
        assertThat(none.from()).isEqualTo(today.minusDays(29));

        ReportPeriod onlyFrom = ReportPeriod.of(LocalDate.of(2020, 3, 1), null, CAIRO, 31);
        assertThat(onlyFrom.to()).isEqualTo(LocalDate.of(2020, 3, 30));

        ReportPeriod onlyTo = ReportPeriod.of(null, LocalDate.of(2020, 3, 30), CAIRO, 31);
        assertThat(onlyTo.from()).isEqualTo(LocalDate.of(2020, 3, 1));
    }

    @Test
    void invalidRanges_areRejected() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2020, 3, 2), LocalDate.of(2020, 3, 1), CAIRO, 31))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("must not be after");
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), CAIRO, 31))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("32 days; at most 31");
    }
}
