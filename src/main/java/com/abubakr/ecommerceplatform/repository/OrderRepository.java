package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Customer lookups always include the user id, so another user's order is indistinguishable from a missing one.
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
