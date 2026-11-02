package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.gateway.WebhookRequest;
import com.abubakr.ecommerceplatform.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Payment webhooks", description = "Called by Stripe and Paymob; authenticated by signature, not by JWT")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    // 2xx tells the provider "received, don't retry"; 400 for bad signatures; 5xx makes it retry later.
    @PostMapping("/{provider}")
    @Operation(summary = "Receive a payment notification (stripe or paymob)")
    public ResponseEntity<Void> receive(@PathVariable String provider,
                                        @RequestBody String body,
                                        @RequestHeader HttpHeaders headers,
                                        @RequestParam Map<String, String> queryParams) {
        paymentService.handleWebhook(parseProvider(provider), new WebhookRequest(body, headers, queryParams));
        return ResponseEntity.ok().build();
    }

    private static PaymentProvider parseProvider(String value) {
        try {
            return PaymentProvider.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Unknown payment provider");
        }
    }
}
