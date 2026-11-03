package com.abubakr.ecommerceplatform.email;

import lombok.Builder;
import lombok.Singular;

import java.util.List;

// What an email says. Every email uses the same template; blocks left empty are simply not rendered.
@Builder
public record EmailContent(
        String headline,
        @Singular List<String> paragraphs,
        @Singular List<ItemLine> items,
        String total,
        @Singular List<String> shippingLines,
        @Singular List<Detail> details,
        String actionLabel,
        String actionUrl
) {

    public record ItemLine(String name, int quantity, String unitPrice, String subtotal) {
    }

    public record Detail(String label, String value) {
    }
}
