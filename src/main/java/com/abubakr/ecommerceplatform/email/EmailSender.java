package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    // Only delivery failures are retried (server down, connection reset); a badly built message would fail every time.
    @Retryable(includes = MailSendException.class,
            maxRetriesString = "${app.mail.retry.max-retries}",
            delayString = "${app.mail.retry.delay-ms}",
            multiplier = 2)
    public void send(EmailMessage email) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
        } catch (MessagingException e) {
            throw new MailPreparationException("Could not build email '" + email.subject() + "'", e);
        }
        mailSender.send(message);
    }
}
