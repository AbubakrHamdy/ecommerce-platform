package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Customer lookups always include the user id, so another user's order is indistinguishable from a missing one.
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // PENDING orders past the payment window, unless the customer still has an open payment page.
    @Query("""
            select o.id from Order o
            where o.status = com.abubakr.ecommerceplatform.enums.OrderStatus.PENDING
              and o.createdAt < :cutoff
              and not exists (
                  select p.id from Payment p
                  where p.order = o
                    and p.status = com.abubakr.ecommerceplatform.enums.PaymentStatus.PENDING
                    and p.expiresAt > :now)
            order by o.id""")
    List<Long> findIdsToExpire(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
