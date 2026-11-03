package com.abubakr.ecommerceplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;

public final class Money {

    private Money() {
    }

    // "EGP 1,249.50", "USD 19.99", "JPY 500": the code is unambiguous across markets, unlike "$" or "£".
    public static String format(BigDecimal amount, String currencyCode) {
        int fractionDigits = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setMinimumFractionDigits(fractionDigits);
        format.setMaximumFractionDigits(fractionDigits);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return currencyCode + " " + format.format(amount);
    }

    // Providers take integer minor units (cents, piasters); the digits depend on the currency (JPY 0, EGP 2, KWD 3).
    public static long toMinorUnits(BigDecimal amount, String currencyCode) {
        int fractionDigits = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        return amount.movePointRight(fractionDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
