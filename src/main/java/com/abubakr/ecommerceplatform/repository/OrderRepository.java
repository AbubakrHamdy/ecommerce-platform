package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
