package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import com.abubakr.ecommerceplatform.config.OrderProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.OrderItem;
import com.abubakr.ecommerceplatform.entity.ShippingAddress;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.CancellationReason;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import com.abubakr.ecommerceplatform.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Renders the real templates, so these tests also catch broken template expressions.
class OrderEmailsTest {

    private final OrderEmails emails = new OrderEmails(
            Mockito.mock(OrderRepository.class),
            new EmailRenderer(htmlEngine(), emailProperties()),
            emailProperties(),
            new OrderProperties(Duration.ofMinutes(30), Duration.ofSeconds(60)));

    @Test
    void orderPlaced_asksForPaymentWithItemsTotalAndDeadline() {
        EmailMessage email = emails.build(order(), OrderStatusChanged.to(12L, OrderStatus.PENDING)).orElseThrow();

        assertThat(email.to()).isEqualTo("jane@example.com");
        assertThat(email.subject()).isEqualTo("Order #12 received: complete your payment");
        assertThat(email.html()).contains("Thanks for your order, Jane!", "Wireless Mouse", "EGP 39.98", "EGP 1,249.97",
                "within 30 minutes", "http://shop/orders/12", "9 Nile Corniche", "Giza 12511");
        assertThat(email.text()).contains("Wireless Mouse x 2 @ EGP 19.99 = EGP 39.98", "Total: EGP 1,249.97",
                "Complete your payment: http://shop/orders/12");
    }

    @Test
    void paid_isAReceipt() {
        EmailMessage email = emails.build(order(), OrderStatusChanged.to(12L, OrderStatus.PAID)).orElseThrow();

        assertThat(email.subject()).isEqualTo("Payment received for order #12");
        assertThat(email.html()).contains("your order is confirmed", "payment of EGP 1,249.97", "ProBook 14 Laptop");
    }

    @Test
    void shippedAndDelivered_haveTheirOwnMessages() {
        assertThat(emails.build(order(), OrderStatusChanged.to(12L, OrderStatus.SHIPPED)).orElseThrow().subject())
                .isEqualTo("Your order #12 is on its way");
        assertThat(emails.build(order(), OrderStatusChanged.to(12L, OrderStatus.DELIVERED)).orElseThrow().subject())
                .isEqualTo("Your order #12 was delivered");
    }

    @Test
    void processing_sendsNoEmail() {
        assertThat(emails.build(order(), OrderStatusChanged.to(12L, OrderStatus.PROCESSING))).isEmpty();
    }

    @Test
    void cancellation_explainsWhy() {
        assertThat(cancelled(CancellationReason.CUSTOMER)).contains("You cancelled this order");
        assertThat(cancelled(CancellationReason.STORE)).contains("payment will be refunded");
        assertThat(cancelled(CancellationReason.PAYMENT_EXPIRED)).contains("didn't receive your payment within 30 minutes");
    }

    @Test
    void productNamesAreHtmlEscaped() {
        Order order = order();
        order.getItems().getFirst().setProductName("<script>alert(1)</script>");

        EmailMessage email = emails.build(order, OrderStatusChanged.to(12L, OrderStatus.PAID)).orElseThrow();

        assertThat(email.html()).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
    }

    private String cancelled(CancellationReason reason) {
        Optional<EmailMessage> email = emails.build(order(), OrderStatusChanged.cancelled(12L, reason));
        assertThat(email).get().extracting(EmailMessage::subject).isEqualTo("Your order #12 was cancelled");
        return email.orElseThrow().text();
    }

    private static Order order() {
        User user = User.builder().firstName("Jane").email("jane@example.com").build();
        Order order = Order.builder()
                .user(user).status(OrderStatus.PENDING).currency("EGP")
                .totalAmount(new BigDecimal("1249.97"))
                .shippingAddress(new ShippingAddress("9 Nile Corniche", "Giza", null, "12511", "Egypt"))
                .build();
        order.setId(12L);
        order.addItem(OrderItem.builder().productName("Wireless Mouse").priceAtPurchase(new BigDecimal("19.99")).quantity(2).build());
        order.addItem(OrderItem.builder().productName("ProBook 14 Laptop").priceAtPurchase(new BigDecimal("1209.99")).quantity(1).build());
        return order;
    }

    private static EmailProperties emailProperties() {
        return new EmailProperties("no-reply@shop.test", "Test Shop", "http://shop/orders/{orderId}");
    }

    private static SpringTemplateEngine htmlEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
