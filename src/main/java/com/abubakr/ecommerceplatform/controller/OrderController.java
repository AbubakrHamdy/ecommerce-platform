package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.order.CheckoutRequest;
import com.abubakr.ecommerceplatform.dto.order.OrderResponse;
import com.abubakr.ecommerceplatform.dto.order.OrderSummaryResponse;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping
    @Operation(summary = "List my orders (sortable by createdAt or totalAmount)")
    public PageResponse<OrderSummaryResponse> myOrders(
            @CurrentUserId Long userId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.myOrders(userId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my orders")
    public OrderResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderService.getMyOrder(userId, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel my order while it is still PENDING (stock is put back)")
    public OrderResponse cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        return orderService.cancelMyOrder(userId, id);
    }
}
