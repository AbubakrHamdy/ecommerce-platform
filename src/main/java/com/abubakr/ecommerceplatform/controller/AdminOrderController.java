package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.order.OrderResponse;
import com.abubakr.ecommerceplatform.dto.order.OrderSummaryResponse;
import com.abubakr.ecommerceplatform.dto.order.UpdateOrderStatusRequest;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Orders")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List all orders, optionally filtered by status")
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listAll(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any order")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getAnyOrder(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order to its next status",
            description = "PENDING→PAID|CANCELLED, PAID→PROCESSING|CANCELLED, PROCESSING→SHIPPED|CANCELLED, SHIPPED→DELIVERED. "
                    + "Cancelling puts the stock back. Other moves return 409.")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
