package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import com.abubakr.ecommerceplatform.entity.Order;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import com.abubakr.ecommerceplatform.event.PaymentNeedsReview;
import com.abubakr.ecommerceplatform.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminEmailsTest {

    @Test
    void reviewAlert_goesToTheAdminWithEverythingNeededToRefund() {
        EmailProperties properties = properties("owner@shop.test");
        AdminEmails emails = new AdminEmails(mock(PaymentRepository.class), renderer(properties), properties);

        EmailMessage email = emails.build(flaggedPayment());

        assertThat(email.to()).isEqualTo("owner@shop.test");
        assertThat(email.subject()).isEqualTo("Action needed: payment #5 for order #12");
        assertThat(email.text()).contains("refund required", "Order: #12 (PAID)", "Customer: jane@example.com",
                "Provider: STRIPE", "Amount: EGP 59.97", "Transaction: pi_123");
    }

    @Test
    void withoutAnAdminAddress_noAlertIsBuilt() {
        PaymentRepository payments = mock(PaymentRepository.class);
        EmailProperties properties = properties("");
        AdminEmails emails = new AdminEmails(payments, renderer(properties), properties);

        assertThat(emails.forPaymentReview(new PaymentNeedsReview(5L))).isEmpty();
        verifyNoInteractions(payments);
    }

    private static Payment flaggedPayment() {
        Order order = Order.builder().status(OrderStatus.PAID)
                .user(User.builder().email("jane@example.com").build()).build();
        order.setId(12L);
        Payment payment = Payment.builder().order(order).provider(PaymentProvider.STRIPE).status(PaymentStatus.SUCCEEDED)
                .amount(new BigDecimal("59.97")).currency("EGP").transactionId("pi_123")
                .reviewNote("Order was PAID when this payment succeeded; refund required").build();
        payment.setId(5L);
        return payment;
    }

    private static EmailProperties properties(String admin) {
        return new EmailProperties("no-reply@shop.test", "Test Shop", "http://shop/orders/{orderId}", admin);
    }

    private static EmailRenderer renderer(EmailProperties properties) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new EmailRenderer(engine, properties);
    }
}
