package com.abubakr.ecommerceplatform.dto.payment;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record StartPaymentRequest(
        @Schema(description = "Omit to use the store's default provider (see GET /api/v1/store)")
        PaymentProvider provider
) {}
