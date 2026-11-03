package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.StoreProperties;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// One deployment serves one country: its currency and payment providers follow from the configured country.
@Component
public class StoreSettings {

    private static final Set<String> PAYMOB_COUNTRIES = Set.of("EG", "SA", "AE", "OM", "PK");

    private final String country;
    private final Currency currency;
    private final List<PaymentProvider> paymentProviders;
    private final ZoneId timeZone;

    public StoreSettings(StoreProperties properties) {
        this.country = validCountry(properties.country());
        this.currency = StringUtils.hasText(properties.currency())
                ? validCurrency(properties.currency())
                : currencyOf(country);
        this.paymentProviders = properties.paymentProviders() == null || properties.paymentProviders().isEmpty()
                ? defaultProvidersFor(country)
                : List.copyOf(properties.paymentProviders());
        this.timeZone = StringUtils.hasText(properties.timeZone()) ? validZone(properties.timeZone()) : ZoneOffset.UTC;
    }

    public ZoneId timeZone() {
        return timeZone;
    }

    public String country() {
        return country;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public List<PaymentProvider> paymentProviders() {
        return paymentProviders;
    }

    static List<PaymentProvider> defaultProvidersFor(String country) {
        return PAYMOB_COUNTRIES.contains(country) ? List.of(PaymentProvider.PAYMOB) : List.of(PaymentProvider.STRIPE);
    }

    private static String validCountry(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalStateException("app.store.country must be set to an ISO 3166 country code, e.g. EG, US or DE");
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
        if (!Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).contains(upper)) {
            throw new IllegalStateException("app.store.country '" + code + "' is not an ISO 3166 country code");
        }
        return upper;
    }

    private static Currency currencyOf(String country) {
        Currency currency = Currency.getInstance(Locale.of("", country));
        if (currency == null) {
            throw new IllegalStateException("Country " + country + " has no official currency; set app.store.currency");
        }
        return currency;
    }

    private static ZoneId validZone(String id) {
        try {
            return ZoneId.of(id.trim());
        } catch (DateTimeException e) {
            throw new IllegalStateException("app.store.time-zone '" + id + "' is not a time zone id such as Africa/Cairo", e);
        }
    }

    private static Currency validCurrency(String code) {
        try {
            return Currency.getInstance(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.store.currency '" + code + "' is not an ISO 4217 currency code", e);
        }
    }
}
