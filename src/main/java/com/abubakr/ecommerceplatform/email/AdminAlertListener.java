package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.AsyncConfig;
import com.abubakr.ecommerceplatform.event.PaymentNeedsReview;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AdminAlertListener {

    private static final Logger log = LoggerFactory.getLogger(AdminAlertListener.class);

    private final AdminEmails adminEmails;
    private final EmailSender emailSender;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentNeedsReview(PaymentNeedsReview event) {
        try {
            adminEmails.forPaymentReview(event).ifPresentOrElse(emailSender::send,
                    () -> log.warn("Payment {} needs review but no admin email is configured (app.mail.admin)", event.paymentId()));
        } catch (RuntimeException e) {
            log.error("Could not send the review alert for payment {}", event.paymentId(), e);
        }
    }
}
