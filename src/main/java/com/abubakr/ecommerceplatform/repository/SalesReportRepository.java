package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    // Native because JPQL can't truncate to a day in another time zone. created_at holds UTC wall-clock time:
    // "at time zone 'UTC'" makes it an instant and "at time zone :zone" turns that into the store's local time.
    // start and end are UTC LocalDateTimes so the driver compares them with the column as they are.
    @Query(nativeQuery = true, value = """
            select cast(date_trunc(:unit, (o.created_at at time zone 'UTC') at time zone :zone) as date) as bucket,
                   count(*) as orders,
                   sum(o.total_amount) as amount
            from orders o
            where o.currency = :currency and o.status in (:statuses)
              and o.created_at >= :start and o.created_at < :end
            group by bucket
            order by bucket""")
    List<BucketTotal> salesByBucket(@Param("unit") String unit,
                                    @Param("zone") String zone,
                                    @Param("currency") String currency,
                                    @Param("statuses") Collection<String> statuses,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    // Grouped by product id, not by the name snapshot, so a renamed product stays one row.
    @Query("""
            select p.id as productId, p.name as productName,
                   sum(i.quantity) as units, sum(i.priceAtPurchase * i.quantity) as revenue
            from OrderItem i join i.order o join i.product p
            where o.currency = :currency and o.status in :statuses
              and o.createdAt >= :start and o.createdAt < :end
            group by p.id, p.name
            order by sum(i.priceAtPurchase * i.quantity) desc, sum(i.quantity) desc, p.id""")
    List<ProductTotal> topProductsByRevenue(@Param("currency") String currency,
                                            @Param("statuses") Collection<OrderStatus> statuses,
                                            @Param("start") Instant start,
                                            @Param("end") Instant end,
                                            Limit limit);

    @Query("""
            select p.id as productId, p.name as productName,
                   sum(i.quantity) as units, sum(i.priceAtPurchase * i.quantity) as revenue
            from OrderItem i join i.order o join i.product p
            where o.currency = :currency and o.status in :statuses
              and o.createdAt >= :start and o.createdAt < :end
            group by p.id, p.name
            order by sum(i.quantity) desc, sum(i.priceAtPurchase * i.quantity) desc, p.id""")
    List<ProductTotal> topProductsByUnits(@Param("currency") String currency,
                                          @Param("statuses") Collection<OrderStatus> statuses,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end,
                                          Limit limit);

    interface StatusTotal {
        OrderStatus getStatus();

        Long getOrders();

        BigDecimal getAmount();
    }

    interface BucketTotal {
        LocalDate getBucket();

        Long getOrders();

        BigDecimal getAmount();
    }

    interface ProductTotal {
        Long getProductId();

        String getProductName();

        Long getUnits();

        BigDecimal getRevenue();
    }
}
