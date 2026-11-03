package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.CacheNames;
import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.order.CheckoutRequest;
import com.abubakr.ecommerceplatform.dto.order.OrderResponse;
import com.abubakr.ecommerceplatform.dto.order.OrderSummaryResponse;
import com.abubakr.ecommerceplatform.entity.Address;
import com.abubakr.ecommerceplatform.entity.Cart;
import com.abubakr.ecommerceplatform.entity.CartItem;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
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
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final CacheManager cacheManager;
    private final StoreSettings storeSettings;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;

    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .filter(found -> !found.getItems().isEmpty())
                .orElseThrow(() -> new BadRequestException("Your cart is empty"));
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        Order order = Order.builder()
                .user(cart.getUser())
                .status(OrderStatus.PENDING)
                .currency(storeSettings.currencyCode())
                .shippingAddress(ShippingAddress.copyOf(address))
                .build();

        // Same product order in every transaction, so two checkouts can never wait on each other's rows (deadlock).
        List<CartItem> lines = cart.getItems().stream()
                .sorted(Comparator.comparing(line -> line.getProduct().getId()))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem line : lines) {
            Product product = line.getProduct();
            reserve(product, line.getQuantity());
            order.addItem(OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .priceAtPurchase(product.getPrice())
                    .quantity(line.getQuantity())
                    .build());
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        cart.getItems().clear();
        events.publishEvent(OrderStatusChanged.to(saved.getId(), OrderStatus.PENDING));
        return orderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> myOrders(Long userId, Pageable pageable) {
        return PageResponse.of(orderRepository.findByUserId(userId, Pageables.withAllowedSort(pageable, SORTABLE_FIELDS))
                .map(orderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long userId, Long orderId) {
        return orderMapper.toResponse(findOwnOrder(userId, orderId));
    }

    @Transactional
    public OrderResponse cancelMyOrder(Long userId, Long orderId) {
        Order order = findOwnOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Only pending orders can be cancelled; please contact support to cancel a paid order");
        }
        cancel(order, CancellationReason.CUSTOMER);
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listAll(OrderStatus status, Pageable pageable) {
        Pageable sorted = Pageables.withAllowedSort(pageable, SORTABLE_FIELDS);
        return PageResponse.of((status == null ? orderRepository.findAll(sorted) : orderRepository.findByStatus(status, sorted))
                .map(orderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getAnyOrder(Long orderId) {
        return orderMapper.toResponse(findOrder(orderId));
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus next) {
        Order order = findOrder(orderId);
        OrderStatus current = order.getStatus();
        if (current == next) {
            throw new ConflictException("The order is already " + next);
        }
        if (!current.canMoveTo(next)) {
            throw new ConflictException("Cannot change order status from " + current + " to " + next);
        }
        if (next == OrderStatus.CANCELLED) {
            cancel(order, CancellationReason.STORE);
        } else {
            order.setStatus(next);
            events.publishEvent(OrderStatusChanged.to(order.getId(), next));
        }
        return orderMapper.toResponse(order);
    }

    // Called once per order by OrderExpiryJob, each in its own transaction.
    @Transactional
    public boolean expireIfStillPending(Long orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        cancel(order, CancellationReason.PAYMENT_EXPIRED);
        return true;
    }

    // Puts reserved stock back; products are released in id order, matching checkout, to avoid deadlocks.
    void cancel(Order order, CancellationReason reason) {
        order.setStatus(OrderStatus.CANCELLED);
        events.publishEvent(OrderStatusChanged.cancelled(order.getId(), reason));
        paymentService.closeOpenSessions(order.getId());
        order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> {
                    productRepository.releaseStock(item.getProduct().getId(), item.getQuantity());
                    evictCachedProduct(item.getProduct().getId());
                });
    }

    // Stock changes are bulk UPDATEs that bypass @CacheEvict; the transaction-aware cache evicts after commit.
    private void evictCachedProduct(Long productId) {
        Cache products = cacheManager.getCache(CacheNames.PRODUCTS);
        if (products != null) {
            products.evict(productId);
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Order findOwnOrder(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private void reserve(Product product, int quantity) {
        if (productRepository.reserveStock(product.getId(), quantity) == 0) {
            throw new ConflictException(product.isActive()
                    ? "Not enough stock for '" + product.getName() + "'"
                    : "'" + product.getName() + "' is no longer available");
        }
        evictCachedProduct(product.getId());
    }
}
