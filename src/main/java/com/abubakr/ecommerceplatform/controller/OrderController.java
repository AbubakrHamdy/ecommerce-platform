package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.order.CheckoutRequest;
import com.abubakr.ecommerceplatform.dto.order.OrderResponse;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Checkout and the signed-in user's orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Check out: turn the cart into a PENDING order, reserve stock and empty the cart")
    public OrderResponse checkout(@CurrentUserId Long userId, @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(userId, request);
    }
}
