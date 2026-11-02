package com.abubakr.ecommerceplatform.gateway;

import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGateways {

    private final Map<PaymentProvider, PaymentGateway> configured;

    public PaymentGateways(ObjectProvider<PaymentGateway> gateways) {
        this.configured = gateways.orderedStream()
                .filter(PaymentGateway::isConfigured)
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::provider, Function.identity()));
    }

    public Optional<PaymentGateway> find(PaymentProvider provider) {
        return Optional.ofNullable(configured.get(provider));
    }

    public boolean isConfigured(PaymentProvider provider) {
        return configured.containsKey(provider);
    }
}
