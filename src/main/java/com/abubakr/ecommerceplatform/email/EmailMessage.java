package com.abubakr.ecommerceplatform.email;

public record EmailMessage(
        String to,
        String subject,
        String html,
        String text
) {}
