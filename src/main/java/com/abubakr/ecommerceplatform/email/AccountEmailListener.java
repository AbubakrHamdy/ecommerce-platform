package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.AsyncConfig;
import com.abubakr.ecommerceplatform.event.PasswordResetRequested;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);

    private final AccountEmails accountEmails;
    private final EmailSender emailSender;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        try {
            emailSender.send(accountEmails.passwordReset(event));
        } catch (RuntimeException e) {
            log.error("Could not send the password reset email", e);
        }
    }
}
