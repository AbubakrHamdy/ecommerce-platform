package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.order.OrderItemResponse;
import com.abubakr.ecommerceplatform.dto.order.OrderResponse;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productImageUrl", expression = "java(ProductMapper.primaryImageUrl(item.getProduct()))")
    @Mapping(target = "subtotal", expression = "java(subtotal(item))")
    OrderItemResponse toItemResponse(OrderItem item);

    default BigDecimal subtotal(OrderItem item) {
        return item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
