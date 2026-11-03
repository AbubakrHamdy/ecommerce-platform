package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A small Spring context: @Retryable only works through Spring's proxy, so a plain unit test can't show it.
@SpringJUnitConfig(EmailSenderRetryTest.Config.class)
@TestPropertySource(properties = {"app.mail.retry.max-retries=3", "app.mail.retry.delay-ms=10"})
class EmailSenderRetryTest {

    private static final EmailMessage EMAIL = new EmailMessage("jane@example.com", "Hello", "<p>Hi</p>", "Hi");

    @Autowired private EmailSender emailSender;
    @Autowired private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void mailServerDownTwice_thenUp_emailIsStillDelivered() {
        doThrow(new MailSendException("connection refused"))
                .doThrow(new MailSendException("connection refused"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        emailSender.send(EMAIL);

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void mailServerStaysDown_givesUpAfterThreeRetries() {
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailSender.send(EMAIL)).isInstanceOf(MailSendException.class);
        verify(mailSender, times(4)).send(any(MimeMessage.class));
    }

    @Test
    void healthyServer_sendsOnce() {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailSender.send(EMAIL);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Configuration
    @EnableResilientMethods
    static class Config {

        @Bean
        JavaMailSender mailSender() {
            return mock(JavaMailSender.class);
        }

        @Bean
        EmailSender emailSender(JavaMailSender mailSender) {
            return new EmailSender(mailSender, new EmailProperties("no-reply@shop.test", "Test Shop", "http://shop/{orderId}"));
        }
    }
}
