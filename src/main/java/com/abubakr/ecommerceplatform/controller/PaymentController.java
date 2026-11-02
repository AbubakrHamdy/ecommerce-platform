package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.payment.PaymentResponse;
import com.abubakr.ecommerceplatform.dto.payment.PaymentSessionResponse;
import com.abubakr.ecommerceplatform.dto.payment.StartPaymentRequest;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pay for a pending order on the provider's hosted page")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Get a checkout URL for a PENDING order",
            description = "Redirect the customer to checkoutUrl. The order becomes PAID only when the provider's "
                    + "signed webhook arrives, never because the browser returned to a success page. "
                    + "Calling this again returns the still-open session instead of creating a new one.")
    public PaymentSessionResponse start(@CurrentUserId Long userId, @PathVariable Long orderId,
                                        @RequestBody(required = false) StartPaymentRequest request) {
        return paymentService.startPayment(userId, orderId, request == null ? null : request.provider());
    }

    @GetMapping
    @Operation(summary = "Payment attempts for my order")
    public List<PaymentResponse> list(@CurrentUserId Long userId, @PathVariable Long orderId) {
        return paymentService.listPayments(userId, orderId);
    }
}
