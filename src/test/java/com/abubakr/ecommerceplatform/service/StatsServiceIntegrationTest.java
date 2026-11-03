package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.stats.RevenuePoint;
import com.abubakr.ecommerceplatform.dto.stats.RevenueSeriesResponse;
import com.abubakr.ecommerceplatform.dto.stats.SalesSummaryResponse;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.abubakr.ecommerceplatform.enums.OrderStatus.CANCELLED;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.DELIVERED;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PAID;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PENDING;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.PROCESSING;
import static com.abubakr.ecommerceplatform.enums.OrderStatus.SHIPPED;
import static com.abubakr.ecommerceplatform.enums.ReportGranularity.DAY;
import static com.abubakr.ecommerceplatform.enums.ReportGranularity.MONTH;
import static com.abubakr.ecommerceplatform.enums.ReportGranularity.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real PostgreSQL with the dev store (EG, EGP, Africa/Cairo = UTC+2 in 2020). Orders are dated March 2020 so the
// seed data, which is always recent, never falls inside the reported days.
@SpringBootTest
@ActiveProfiles("dev")
class StatsServiceIntegrationTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2020, 3, 1);
    private static final LocalDate MARCH_2 = LocalDate.of(2020, 3, 2);

    @Autowired private StatsService statsService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> createdOrders = new ArrayList<>();

    @BeforeEach
    void createOrders() {
        // created_at holds UTC; the comments give the Cairo wall clock.
        order(PAID, "100.00", "EGP", "2020-03-01 10:00:00");        // 1 March 12:00
        order(DELIVERED, "50.50", "EGP", "2020-03-01 21:59:59");    // 1 March 23:59:59
        order(SHIPPED, "30.00", "EGP", "2020-03-01 22:00:00");      // 2 March 00:00, the next day
        order(PENDING, "999.00", "EGP", "2020-03-01 12:00:00");     // not paid
        order(CANCELLED, "777.00", "EGP", "2020-03-01 12:00:00");   // not a sale
        order(PAID, "10.00", "USD", "2020-03-01 12:00:00");         // another currency
        order(PAID, "5.00", "EGP", "2020-02-29 21:59:59");          // 29 February 23:59:59, the day before
    }

    @AfterEach
    void cleanUp() {
        createdOrders.forEach(id -> {
            jdbcTemplate.update("delete from order_items where order_id = ?", id);
            jdbcTemplate.update("delete from orders where id = ?", id);
        });
    }

    @Test
    void summary_countsPaidOrdersOfTheStoreCurrencyOnTheCairoCalendarDay() {
        SalesSummaryResponse summary = statsService.summary(MARCH_1, MARCH_1);

        assertThat(summary.currency()).isEqualTo("EGP");
        assertThat(summary.revenue()).isEqualByComparingTo("150.50");
        assertThat(summary.paidOrders()).isEqualTo(2);
        assertThat(summary.averageOrderValue()).isEqualTo(new BigDecimal("75.25"));
        assertThat(summary.ordersByStatus()).containsExactly(
                entry(PENDING, 1), entry(PAID, 1), entry(PROCESSING, 0),
                entry(SHIPPED, 0), entry(DELIVERED, 1), entry(CANCELLED, 1));
    }

    @Test
    void summary_ofSeveralDays_includesBothEnds() {
        SalesSummaryResponse summary = statsService.summary(MARCH_1, MARCH_2);

        assertThat(summary.revenue()).isEqualByComparingTo("180.50");
        assertThat(summary.paidOrders()).isEqualTo(3);
        assertThat(summary.averageOrderValue()).isEqualTo(new BigDecimal("60.17"));
    }

    @Test
    void summary_ofADayWithoutOrders_isZeroWithTheCurrencyScale() {
        SalesSummaryResponse summary = statsService.summary(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1));

        assertThat(summary.revenue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.averageOrderValue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.paidOrders()).isZero();
        assertThat(summary.ordersByStatus()).hasSize(OrderStatus.values().length).containsValue(0L).doesNotContainValue(1L);
    }

    @Test
    void dailyRevenue_hasOnePointPerDay_includingEmptyDays() {
        RevenueSeriesResponse series = statsService.revenue(LocalDate.of(2020, 2, 28), LocalDate.of(2020, 3, 3), DAY);

        assertThat(series.currency()).isEqualTo("EGP");
        assertThat(series.points()).containsExactly(
                point("2020-02-28", 0, "0.00"),
                point("2020-02-29", 1, "5.00"),
                point("2020-03-01", 2, "150.50"),
                point("2020-03-02", 1, "30.00"),
                point("2020-03-03", 0, "0.00"));
    }

    @Test
    void weeklyRevenue_startsWeeksOnMonday_andOnlyCountsDaysInTheRange() {
        // 29 February 2020 is a Saturday: its week starts on Monday 24 February and ends with Sunday 1 March.
        RevenueSeriesResponse series = statsService.revenue(LocalDate.of(2020, 2, 29), MARCH_2, WEEK);

        assertThat(series.points()).containsExactly(
                point("2020-02-24", 3, "155.50"),
                point("2020-03-02", 1, "30.00"));
    }

    @Test
    void monthlyRevenue_groupsByCalendarMonthInTheStoreTimeZone() {
        RevenueSeriesResponse series = statsService.revenue(LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 31), MONTH);

        assertThat(series.points()).containsExactly(
                point("2020-02-01", 1, "5.00"),
                point("2020-03-01", 3, "180.50"));
    }

    @Test
    void dailyRevenue_isLimitedToAboutAYear() {
        assertThatThrownBy(() -> statsService.revenue(LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 2), DAY))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("at most 366");
        assertThat(statsService.revenue(LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 2), WEEK).points()).hasSize(53);
    }

    private static RevenuePoint point(String date, long paidOrders, String revenue) {
        return new RevenuePoint(LocalDate.parse(date), paidOrders, new BigDecimal(revenue));
    }

    private Long order(OrderStatus status, String total, String currency, String createdAtUtc) {
        Long id = orderRepository.save(Order.builder()
                .user(userRepository.findByEmail("customer@example.com").orElseThrow())
                .status(status)
                .totalAmount(new BigDecimal(total))
                .currency(currency)
                .shippingAddress(new ShippingAddress("1 Test St", "Cairo", null, "11511", "Egypt"))
                .build()).getId();
        // A literal, not a java.sql.Timestamp, so the JVM's zone can't shift it.
        jdbcTemplate.update("update orders set created_at = ?::timestamp where id = ?", createdAtUtc, id);
        createdOrders.add(id);
        return id;
    }

    private static Map.Entry<OrderStatus, Long> entry(OrderStatus status, long count) {
        return Map.entry(status, count);
    }
}
