package com.abubakr.ecommerceplatform.entity;

import com.abubakr.ecommerceplatform.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // A copy taken at checkout, so later address-book edits never rewrite order history.
    @Embedded
    private ShippingAddress shippingAddress;

    // Two concurrent status changes can't both win: the second one fails instead of silently overwriting.
    @Version
    private Long version;

    // items are priced snapshots at checkout time; never cascade-removed or merged once persisted
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> items = new ArrayList<>();

    // payment attempts accumulate on retry and are kept as financial history; never cascade-removed
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<Payment> payments = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addPayment(Payment payment) {
        payments.add(payment);
        payment.setOrder(this);
    }
}
