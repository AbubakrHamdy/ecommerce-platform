package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.cart.AddCartItemRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 2L;

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CartMapper cartMapper;

    private CartService service;
    private Cart cart;
    private Product mouse;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, cartItemRepository, productRepository, userRepository, cartMapper);
        cart = Cart.builder().build();
        cart.setId(1L);
        mouse = product(3L, "Wireless Mouse", 5);
    }

    @Test
    void addItem_newProduct_createsALine() {
        when(productRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mouse));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addItem(USER_ID, new AddCartItemRequest(3L, 2));

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void addItem_productAlreadyInCart_increasesQuantityInsteadOfAddingALine() {
        CartItem line = line(10L, mouse, 2);
        cart.getItems().add(line);
        when(productRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mouse));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        service.addItem(USER_ID, new AddCartItemRequest(3L, 3));

        assertThat(line.getQuantity()).isEqualTo(5);
        assertThat(cart.getItems()).hasSize(1);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_moreThanInStock_isRejectedWithTheAvailableAmount() {
        cart.getItems().add(line(10L, mouse, 4));
        when(productRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mouse));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(3L, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only 5 of 'Wireless Mouse' left in stock");
    }

    @Test
    void addItem_beyondTheLineLimit_isRejected() {
        Product plenty = product(4L, "T-Shirt", 1000);
        cart.getItems().add(line(11L, plenty, 98));
        when(productRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(plenty));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(4L, 2)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addItem_archivedProduct_isNotFound() {
        when(productRepository.findByIdAndActiveTrue(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER_ID, new AddCartItemRequest(6L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_lineFromSomeoneElsesCart_isNotFound() {
        cart.getItems().add(line(10L, mouse, 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.updateItem(USER_ID, 999L, new UpdateCartItemRequest(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cart item not found");
    }

    @Test
    void getCart_userWithoutCart_getsANewEmptyOne() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        service.getCart(USER_ID);

        verify(cartRepository).save(any(Cart.class));
        verify(userRepository).getReferenceById(USER_ID);
    }

    private static Product product(Long id, String name, int stock) {
        Product product = Product.builder().name(name).price(new BigDecimal("19.99")).stock(stock).active(true).build();
        product.setId(id);
        return product;
    }

    private CartItem line(Long id, Product product, int quantity) {
        CartItem item = CartItem.builder().cart(cart).product(product).quantity(quantity).build();
        item.setId(id);
        return item;
    }
}
