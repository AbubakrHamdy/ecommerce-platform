package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.StoreProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static com.abubakr.ecommerceplatform.enums.PaymentProvider.PAYMOB;
import static com.abubakr.ecommerceplatform.enums.PaymentProvider.STRIPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreSettingsTest {

    @Test
    void middleEastStores_useTheirLocalCurrencyAndPaymob() {
        assertStore("EG", "EGP", List.of(PAYMOB));
        assertStore("sa", "SAR", List.of(PAYMOB));
        assertStore("AE", "AED", List.of(PAYMOB));
    }

    @Test
    void europeanAndAmericanStores_useTheirCurrencyAndStripe() {
        assertStore("US", "USD", List.of(STRIPE));
        assertStore("DE", "EUR", List.of(STRIPE));
        assertStore("FR", "EUR", List.of(STRIPE));
        assertStore("GB", "GBP", List.of(STRIPE));
    }

    @Test
    void currencyAndProvidersCanBeOverridden() {
        StoreSettings settings = new StoreSettings(new StoreProperties("AE", "usd", List.of(STRIPE, PAYMOB), null));

        assertThat(settings.currencyCode()).isEqualTo("USD");
        assertThat(settings.paymentProviders()).containsExactly(STRIPE, PAYMOB);
    }

    @Test
    void timeZone_defaultsToUtcAndAcceptsIanaIds() {
        assertThat(new StoreSettings(new StoreProperties("EG", null, null, null)).timeZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(new StoreSettings(new StoreProperties("EG", null, null, " ")).timeZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(new StoreSettings(new StoreProperties("EG", null, null, "Africa/Cairo")).timeZone())
                .isEqualTo(ZoneId.of("Africa/Cairo"));
    }

    @Test
    void invalidConfiguration_failsAtStartupWithAClearMessage() {
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("EG", null, null, "Cairo/Egypt")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a time zone id");
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("", null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be set");
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("XX", null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not an ISO 3166");
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("EG", "DOLLARS", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not an ISO 4217");
    }

    private static void assertStore(String country, String currency, List<?> providers) {
        StoreSettings settings = new StoreSettings(new StoreProperties(country, null, null, null));
        assertThat(settings.country()).isEqualTo(country.toUpperCase());
        assertThat(settings.currencyCode()).isEqualTo(currency);
        assertThat(settings.paymentProviders()).isEqualTo(providers);
    }
}
