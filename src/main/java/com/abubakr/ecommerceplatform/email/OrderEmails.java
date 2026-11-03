package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import com.abubakr.ecommerceplatform.config.OrderProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.enums.CancellationReason;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import com.abubakr.ecommerceplatform.service.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Decides what each order email says. Reloads the order, so the email shows exactly what was committed.
@Component
@RequiredArgsConstructor
public class OrderEmails {

    private final OrderRepository orderRepository;
    private final EmailRenderer renderer;
    private final EmailProperties emailProperties;
    private final OrderProperties orderProperties;

    @Transactional(readOnly = true)
    public Optional<EmailMessage> forStatusChange(OrderStatusChanged event) {
        return orderRepository.findById(event.orderId()).flatMap(order -> build(order, event));
    }

    Optional<EmailMessage> build(Order order, OrderStatusChanged event) {
        Long id = order.getId();
        String firstName = order.getUser().getFirstName();
        String total = money(order.getTotalAmount(), order);

        EmailContent.EmailContentBuilder content = EmailContent.builder();
        String subject;
        switch (event.status()) {
            case PENDING -> {
                subject = "Order #" + id + " received: complete your payment";
                content.headline("Thanks for your order, " + firstName + "!")
                        .paragraph("We've reserved your items. Please complete your payment within "
                                + Durations.humanize(orderProperties.paymentTimeout())
                                + "; after that the order is cancelled and the items are released.")
                        .items(itemLines(order)).total(total).shippingLines(shippingLines(order))
                        .actionLabel("Complete your payment").actionUrl(emailProperties.orderUrlFor(id));
            }
            case PAID -> {
                subject = "Payment received for order #" + id;
                content.headline("Payment received, your order is confirmed")
                        .paragraph("Hi " + firstName + ", we've received your payment of " + total
                                + ". Here is your receipt; we'll let you know when your order ships.")
                        .items(itemLines(order)).total(total).shippingLines(shippingLines(order))
                        .actionLabel("View your order").actionUrl(emailProperties.orderUrlFor(id));
            }
            case SHIPPED -> {
                subject = "Your order #" + id + " is on its way";
                content.headline("Your order is on its way")
                        .paragraph("Good news, " + firstName + ": your order has shipped and is heading to you.")
                        .items(itemLines(order)).shippingLines(shippingLines(order))
                        .actionLabel("View your order").actionUrl(emailProperties.orderUrlFor(id));
            }
            case DELIVERED -> {
                subject = "Your order #" + id + " was delivered";
                content.headline("Your order was delivered")
                        .paragraph("Hi " + firstName + ", your order has been delivered. We hope you enjoy it, and thank you for shopping with us.")
                        .actionLabel("View your order").actionUrl(emailProperties.orderUrlFor(id));
            }
            case CANCELLED -> {
                subject = "Your order #" + id + " was cancelled";
                content.headline("Your order was cancelled")
                        .paragraph(cancellationText(event.cancellationReason()))
                        .items(itemLines(order)).total(total)
                        .actionLabel("View your order").actionUrl(emailProperties.orderUrlFor(id));
            }
            case PROCESSING -> {
                return Optional.empty();
            }
            default -> throw new IllegalStateException("No email defined for " + event.status());
        }
        return Optional.of(renderer.render(order.getUser().getEmail(), subject, content.build()));
    }

    private String cancellationText(CancellationReason reason) {
        if (reason == null) {
            return "This order was cancelled.";
        }
        return switch (reason) {
            case CUSTOMER -> "You cancelled this order. Nothing was charged and the items have been released.";
            case STORE -> "We had to cancel this order. If you already paid, the payment will be refunded to your original payment method.";
            case PAYMENT_EXPIRED -> "We didn't receive your payment within " + Durations.humanize(orderProperties.paymentTimeout())
                    + ", so the order was cancelled and the items were released. You're welcome to order again.";
        };
    }

    private static List<EmailContent.ItemLine> itemLines(Order order) {
        return order.getItems().stream()
                .map(item -> new EmailContent.ItemLine(item.getProductName(), item.getQuantity(),
                        money(item.getPriceAtPurchase(), order), money(subtotal(item), order)))
                .toList();
    }

    private static List<String> shippingLines(Order order) {
        ShippingAddress address = order.getShippingAddress();
        List<String> lines = new ArrayList<>();
        lines.add(address.street());
        lines.add(address.city() + (address.state() == null ? "" : ", " + address.state()) + " " + address.postalCode());
        lines.add(address.country());
        return lines;
    }

    private static BigDecimal subtotal(OrderItem item) {
        return item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private static String money(BigDecimal amount, Order order) {
        return Money.format(amount, order.getCurrency());
    }
}
