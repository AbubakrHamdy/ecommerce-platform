package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.cart.CartItemResponse;
import com.abubakr.ecommerceplatform.dto.cart.CartResponse;
import com.abubakr.ecommerceplatform.entity.Cart;
import com.abubakr.ecommerceplatform.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(uses = ProductMapper.class)
public interface CartMapper {

    @Mapping(target = "totalPrice", expression = "java(totalPrice(cart))")
    CartResponse toResponse(Cart cart);

    @Mapping(target = "subtotal", expression = "java(subtotal(item))")
    CartItemResponse toItemResponse(CartItem item);

    // Cart prices are always the current product prices; they are frozen only when an order is placed.
    default BigDecimal subtotal(CartItem item) {
        return item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    default BigDecimal totalPrice(Cart cart) {
        return cart.getItems().stream()
                .map(this::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
