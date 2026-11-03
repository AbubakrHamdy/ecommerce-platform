package com.abubakr.ecommerceplatform.email;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DurationsTest {

    @Test
    void readsNaturallyForAnyConfiguredWindow() {
        assertThat(Durations.humanize(Duration.ofMinutes(30))).isEqualTo("30 minutes");
        assertThat(Durations.humanize(Duration.ofMinutes(1))).isEqualTo("1 minute");
        assertThat(Durations.humanize(Duration.ofSeconds(20))).isEqualTo("20 seconds");
        assertThat(Durations.humanize(Duration.ofSeconds(1))).isEqualTo("1 second");
        assertThat(Durations.humanize(Duration.ofMinutes(90))).isEqualTo("90 minutes");
    }
}
