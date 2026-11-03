package com.abubakr.ecommerceplatform.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void convertsToMinorUnitsUsingTheCurrencysOwnDecimals() {
        assertThat(Money.toMinorUnits(new BigDecimal("59.97"), "EGP")).isEqualTo(5997);
        assertThat(Money.toMinorUnits(new BigDecimal("1199.00"), "USD")).isEqualTo(119900);
        assertThat(Money.toMinorUnits(new BigDecimal("500"), "JPY")).isEqualTo(500);
        assertThat(Money.toMinorUnits(new BigDecimal("1.50"), "KWD")).isEqualTo(1500);
    }

    @Test
    void formatsWithTheCurrencyCodeAndItsOwnDecimals() {
        assertThat(Money.format(new BigDecimal("1249.5"), "EGP")).isEqualTo("EGP 1,249.50");
        assertThat(Money.format(new BigDecimal("19.99"), "USD")).isEqualTo("USD 19.99");
        assertThat(Money.format(new BigDecimal("500"), "JPY")).isEqualTo("JPY 500");
        assertThat(Money.format(new BigDecimal("1.5"), "KWD")).isEqualTo("KWD 1.500");
    }

    @Test
    void neverRoundsMoneySilently() {
        assertThatThrownBy(() -> Money.toMinorUnits(new BigDecimal("10.50"), "JPY"))
                .isInstanceOf(ArithmeticException.class);
    }
}
