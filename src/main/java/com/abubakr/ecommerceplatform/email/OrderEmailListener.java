package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.AsyncConfig;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailListener.class);

    private final OrderEmails orderEmails;
    private final EmailSender emailSender;

    // AFTER_COMMIT: a rolled-back checkout never emails "thanks for your order".
    // @Async: the customer's request doesn't wait for, or fail because of, the mail server.
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        try {
            orderEmails.forStatusChange(event).ifPresent(emailSender::send);
        } catch (RuntimeException e) {
            log.error("Could not send the {} email for order {}; the order itself is unaffected",
                    event.status(), event.orderId(), e);
        }
    }
}
