package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.StoreProperties;
import org.junit.jupiter.api.Test;

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
        StoreSettings settings = new StoreSettings(new StoreProperties("AE", "usd", List.of(STRIPE, PAYMOB)));

        assertThat(settings.currencyCode()).isEqualTo("USD");
        assertThat(settings.paymentProviders()).containsExactly(STRIPE, PAYMOB);
    }

    @Test
    void invalidConfiguration_failsAtStartupWithAClearMessage() {
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be set");
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("XX", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not an ISO 3166");
        assertThatThrownBy(() -> new StoreSettings(new StoreProperties("EG", "DOLLARS", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not an ISO 4217");
    }

    private static void assertStore(String country, String currency, List<?> providers) {
        StoreSettings settings = new StoreSettings(new StoreProperties(country, null, null));
        assertThat(settings.country()).isEqualTo(country.toUpperCase());
        assertThat(settings.currencyCode()).isEqualTo(currency);
        assertThat(settings.paymentProviders()).isEqualTo(providers);
    }
}
