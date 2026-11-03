package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Read-only aggregations for the admin dashboard. Every query takes a half-open [start, end) range on
// orders.created_at, which idx_orders_status_created_at covers, and one currency, because amounts in
// different currencies cannot be added up.
public interface SalesReportRepository extends Repository<Order, Long> {

    @Query("""
            select o.status as status, count(o) as orders, coalesce(sum(o.totalAmount), 0) as amount
            from Order o
            where o.currency = :currency and o.createdAt >= :start and o.createdAt < :end
            group by o.status""")
    List<StatusTotal> totalsByStatus(@Param("currency") String currency,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    interface StatusTotal {
        OrderStatus getStatus();

        Long getOrders();

        BigDecimal getAmount();
    }
}
