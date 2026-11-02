package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.store.StoreResponse;
import com.abubakr.ecommerceplatform.gateway.PaymentGateways;
import com.abubakr.ecommerceplatform.service.StoreSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Store", description = "Country, currency and payment options of this store")
public class StoreController {

    private final StoreSettings storeSettings;
    private final PaymentGateways paymentGateways;

    @GetMapping
    @Operation(summary = "Store country, the currency all prices are in, and the payment providers customers can use")
    public StoreResponse get() {
        return new StoreResponse(
                storeSettings.country(),
                storeSettings.currencyCode(),
                storeSettings.paymentProviders().stream().filter(paymentGateways::isConfigured).toList());
    }
}
