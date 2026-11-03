package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.event.PaymentNeedsReview;
import com.abubakr.ecommerceplatform.repository.PaymentRepository;
import com.abubakr.ecommerceplatform.service.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminEmails {

    private final PaymentRepository paymentRepository;
    private final EmailRenderer renderer;
    private final EmailProperties properties;

    @Transactional(readOnly = true)
    public Optional<EmailMessage> forPaymentReview(PaymentNeedsReview event) {
        if (!StringUtils.hasText(properties.admin())) {
            return Optional.empty();
        }
        return paymentRepository.findById(event.paymentId()).map(this::build);
    }

    EmailMessage build(Payment payment) {
        Long orderId = payment.getOrder().getId();
        EmailContent content = EmailContent.builder()
                .headline("A payment needs your attention")
                .paragraph(payment.getReviewNote())
                .paragraph("Check the payment in the " + payment.getProvider() + " dashboard and refund it if needed.")
                .detail(new EmailContent.Detail("Payment", "#" + payment.getId()))
                .detail(new EmailContent.Detail("Order", "#" + orderId + " (" + payment.getOrder().getStatus() + ")"))
                .detail(new EmailContent.Detail("Customer", payment.getOrder().getUser().getEmail()))
                .detail(new EmailContent.Detail("Provider", payment.getProvider().name()))
                .detail(new EmailContent.Detail("Amount", Money.format(payment.getAmount(), payment.getCurrency())))
                .detail(new EmailContent.Detail("Transaction", payment.getTransactionId() == null ? "-" : payment.getTransactionId()))
                .build();
        String subject = "Action needed: payment #" + payment.getId() + " for order #" + orderId;
        return renderer.render(properties.admin(), subject, content);
    }
}
