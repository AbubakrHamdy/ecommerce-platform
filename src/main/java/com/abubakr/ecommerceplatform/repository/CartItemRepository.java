package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
