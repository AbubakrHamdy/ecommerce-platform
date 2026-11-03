package com.abubakr.ecommerceplatform.event;

import java.time.Duration;

// Carries the one-time link in memory only; the database stores just the token's hash.
public record PasswordResetRequested(
        String email,
        String firstName,
        String resetLink,
        Duration validFor
) {}
