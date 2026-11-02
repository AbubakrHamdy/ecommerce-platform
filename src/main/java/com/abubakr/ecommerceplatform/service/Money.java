package com.abubakr.ecommerceplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class Money {

    private Money() {
    }

    // Providers take integer minor units (cents, piasters); the digits depend on the currency (JPY 0, EGP 2, KWD 3).
    public static long toMinorUnits(BigDecimal amount, String currencyCode) {
        int fractionDigits = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        return amount.movePointRight(fractionDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
