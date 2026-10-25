package com.abubakr.ecommerceplatform.dto.order;

import com.abubakr.ecommerceplatform.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull
        OrderStatus status
) {}
