package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.cart.AddCartItemRequest;
import com.abubakr.ecommerceplatform.dto.cart.CartResponse;
import com.abubakr.ecommerceplatform.dto.cart.UpdateCartItemRequest;
import com.abubakr.ecommerceplatform.entity.Cart;
import com.abubakr.ecommerceplatform.entity.CartItem;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.CartMapper;
import com.abubakr.ecommerceplatform.repository.CartItemRepository;
import com.abubakr.ecommerceplatform.repository.CartRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    static final int MAX_QUANTITY_PER_LINE = 99;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse getCart(Long userId) {
        return cartMapper.toResponse(getOrCreateCart(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Cart cart = getOrCreateCart(userId);

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.quantity();
        checkQuantity(product, newQuantity);

        if (existing.isPresent()) {
            existing.get().setQuantity(newQuantity);
        } else {
            CartItem item = cartItemRepository.save(
                    CartItem.builder().cart(cart).product(product).quantity(newQuantity).build());
            cart.getItems().add(item);
        }
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = findItem(cart, itemId);
        checkQuantity(item.getProduct(), request.quantity());
        item.setQuantity(request.quantity());
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().remove(findItem(cart, itemId));
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public void clear(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> cart.getItems().clear());
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().user(userRepository.getReferenceById(userId)).build()));
    }

    // Looking items up inside the user's own cart means another user's item id is simply "not found".
    private static CartItem findItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
    }

    // Stock is only checked here as a courtesy; it is reserved for real at checkout.
    private static void checkQuantity(Product product, int quantity) {
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new BadRequestException("You can add at most " + MAX_QUANTITY_PER_LINE + " of one product");
        }
        if (!product.isActive()) {
            throw new ConflictException("'" + product.getName() + "' is no longer available");
        }
        if (quantity > product.getStock()) {
            throw new ConflictException(product.getStock() == 0
                    ? "'" + product.getName() + "' is out of stock"
                    : "Only " + product.getStock() + " of '" + product.getName() + "' left in stock");
        }
    }
}
