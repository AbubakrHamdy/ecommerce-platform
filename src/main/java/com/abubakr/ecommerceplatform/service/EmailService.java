package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    @Async
    public void sendPasswordResetEmail(String to, String firstName, String resetLink, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.from());
        message.setTo(to);
        message.setSubject("Reset your password");
        message.setText("""
                Hi %s,

                We received a request to reset your password. Use the link below to choose a new one.
                It expires in %d minutes and can only be used once.

                %s

                If you didn't request this, you can safely ignore this email.
                """.formatted(firstName, validFor.toMinutes(), resetLink));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send password reset email", e);
        }
    }
}
