package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.CacheNames;
import com.abubakr.ecommerceplatform.config.StoreProperties;
import com.abubakr.ecommerceplatform.dto.order.CheckoutRequest;
import com.abubakr.ecommerceplatform.entity.Address;
import com.abubakr.ecommerceplatform.entity.Cart;
import com.abubakr.ecommerceplatform.entity.CartItem;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.CancellationReason;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.OrderMapper;
import com.abubakr.ecommerceplatform.repository.AddressRepository;
import com.abubakr.ecommerceplatform.repository.CartRepository;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 2L;

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private CacheManager cacheManager;
    @Mock private Cache productCache;
    @Mock private PaymentService paymentService;
    @Mock private ApplicationEventPublisher events;

    private OrderService service;
    private Cart cart;
    private Address address;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, cartRepository, addressRepository, productRepository, orderMapper,
                cacheManager, new StoreSettings(new StoreProperties("EG", null, null)), paymentService, events);
        User user = User.builder().email("jane@example.com").build();
        user.setId(USER_ID);
        cart = Cart.builder().user(user).build();
        address = Address.builder().user(user).street("123 Tahrir Street").city("Cairo").postalCode("11511").country("Egypt").build();
        address.setId(1L);
        laptop = product(1L, "ProBook 14 Laptop", "899.99");
        mouse = product(3L, "Wireless Mouse", "19.99");
    }

    @Test
    void checkout_emptyCart_isRejected() {
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.checkout(USER_ID, new CheckoutRequest(1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your cart is empty");
    }

    @Test
    void checkout_withSomeoneElsesAddress_isNotFoundAndReservesNothing() {
        addLine(mouse, 1);
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(USER_ID, new CheckoutRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    void checkout_whenOneProductRunsOut_failsWithoutCreatingAnOrder() {
        addLine(mouse, 2);
        addLine(laptop, 1);
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(address));
        when(productRepository.reserveStock(1L, 1)).thenReturn(1);
        when(productRepository.reserveStock(3L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.checkout(USER_ID, new CheckoutRequest(1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Not enough stock for 'Wireless Mouse'");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(events);
        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    void checkout_success_snapshotsPricesAndAddressAndEmptiesTheCart() {
        addLine(mouse, 2);
        addLine(laptop, 1);
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(address));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        service.checkout(USER_ID, new CheckoutRequest(1L));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        Order order = saved.getValue();
        verify(events).publishEvent(OrderStatusChanged.to(42L, OrderStatus.PENDING));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("939.97");
        assertThat(order.getCurrency()).isEqualTo("EGP");
        assertThat(order.getShippingAddress().street()).isEqualTo("123 Tahrir Street");
        assertThat(order.getItems()).extracting("productName").containsExactly("ProBook 14 Laptop", "Wireless Mouse");
        assertThat(order.getItems()).extracting("priceAtPurchase")
                .containsExactly(new BigDecimal("899.99"), new BigDecimal("19.99"));
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void checkout_reservesStockInProductIdOrderToAvoidDeadlocks() {
        addLine(mouse, 2);
        addLine(laptop, 1);
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(address));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.checkout(USER_ID, new CheckoutRequest(1L));

        InOrder order = inOrder(productRepository);
        order.verify(productRepository).reserveStock(1L, 1);
        order.verify(productRepository).reserveStock(3L, 2);
    }

    @Test
    void checkoutAndCancel_clearCachedProductDetailsSoStockIsNeverStale() {
        when(cacheManager.getCache(CacheNames.PRODUCTS)).thenReturn(productCache);
        addLine(mouse, 2);
        addLine(laptop, 1);
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(address));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.checkout(USER_ID, new CheckoutRequest(1L));
        verify(productCache).evict(1L);
        verify(productCache).evict(3L);

        Order paid = order(OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(paid));
        service.updateStatus(7L, OrderStatus.CANCELLED);
        verify(productCache, times(2)).evict(1L);
        verify(productCache, times(2)).evict(3L);
    }

    @Test
    void getMyOrder_orderOfAnotherUser_isNotFound() {
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyOrder(USER_ID, 7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void cancelMyOrder_pending_cancelsAndPutsStockBack() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));

        service.cancelMyOrder(USER_ID, 7L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(OrderStatusChanged.cancelled(7L, CancellationReason.CUSTOMER));
        verify(paymentService).closeOpenSessions(7L);
        InOrder released = inOrder(productRepository);
        released.verify(productRepository).releaseStock(1L, 1);
        released.verify(productRepository).releaseStock(3L, 2);
    }

    @Test
    void cancelMyOrder_alreadyPaid_isRejectedAndStockUntouched() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelMyOrder(USER_ID, 7L)).isInstanceOf(ConflictException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(productRepository, never()).releaseStock(anyLong(), anyInt());
        verifyNoInteractions(events);
    }

    @Test
    void updateStatus_skippingAStep_isRejected() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.updateStatus(7L, OrderStatus.SHIPPED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cannot change order status from PENDING to SHIPPED");
    }

    @Test
    void updateStatus_toTheSameStatus_isRejected() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(OrderStatus.PAID)));

        assertThatThrownBy(() -> service.updateStatus(7L, OrderStatus.PAID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The order is already PAID");
        verifyNoInteractions(events);
    }

    @Test
    void updateStatus_forwardMove_changesStatusWithoutTouchingStock() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        service.updateStatus(7L, OrderStatus.PROCESSING);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(events).publishEvent(OrderStatusChanged.to(7L, OrderStatus.PROCESSING));
        verify(productRepository, never()).releaseStock(anyLong(), anyInt());
    }

    @Test
    void updateStatus_adminCancelsAPaidOrder_putsStockBack() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        service.updateStatus(7L, OrderStatus.CANCELLED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(OrderStatusChanged.cancelled(7L, CancellationReason.STORE));
        verify(productRepository).releaseStock(1L, 1);
        verify(productRepository).releaseStock(3L, 2);
    }

    @Test
    void expireIfStillPending_paidInTheMeantime_isLeftAlone() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThat(service.expireIfStillPending(7L)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(productRepository, never()).releaseStock(anyLong(), anyInt());
        verifyNoInteractions(events);
    }

    @Test
    void expireIfStillPending_unpaid_isCancelledAndRestocked() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThat(service.expireIfStillPending(7L)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(OrderStatusChanged.cancelled(7L, CancellationReason.PAYMENT_EXPIRED));
        verify(productRepository).releaseStock(3L, 2);
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().status(status).build();
        order.setId(7L);
        order.addItem(OrderItem.builder().product(mouse).productName("Wireless Mouse")
                .priceAtPurchase(new BigDecimal("19.99")).quantity(2).build());
        order.addItem(OrderItem.builder().product(laptop).productName("ProBook 14 Laptop")
                .priceAtPurchase(new BigDecimal("899.99")).quantity(1).build());
        return order;
    }

    private void addLine(Product product, int quantity) {
        cart.getItems().add(CartItem.builder().cart(cart).product(product).quantity(quantity).build());
    }

    private static Product product(Long id, String name, String price) {
        Product product = Product.builder().name(name).price(new BigDecimal(price)).stock(10).active(true).build();
        product.setId(id);
        return product;
    }
}
