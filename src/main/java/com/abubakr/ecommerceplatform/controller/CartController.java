package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.cart.AddCartItemRequest;
import com.abubakr.ecommerceplatform.dto.cart.CartResponse;
import com.abubakr.ecommerceplatform.dto.cart.UpdateCartItemRequest;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "The signed-in user's shopping cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get my cart with current prices and total")
    public CartResponse get(@CurrentUserId Long userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product (adding it again increases the quantity)")
    public CartResponse addItem(@CurrentUserId Long userId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(userId, request);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Change the quantity of a cart line")
    public CartResponse updateItem(@CurrentUserId Long userId, @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(userId, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove a line from the cart")
    public CartResponse removeItem(@CurrentUserId Long userId, @PathVariable Long itemId) {
        return cartService.removeItem(userId, itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Empty the cart")
    public void clear(@CurrentUserId Long userId) {
        cartService.clear(userId);
    }
}
