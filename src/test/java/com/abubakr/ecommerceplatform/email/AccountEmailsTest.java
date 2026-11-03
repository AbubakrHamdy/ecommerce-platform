package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.config.EmailProperties;
import com.abubakr.ecommerceplatform.event.PasswordResetRequested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEmailsTest {

    @Test
    void passwordReset_containsTheOneTimeLinkAndItsExpiry() {
        AccountEmails emails = new AccountEmails(renderer());

        EmailMessage email = emails.passwordReset(new PasswordResetRequested(
                "jane@example.com", "Jane", "http://shop/reset-password?token=abc_123", Duration.ofMinutes(30)));

        assertThat(email.to()).isEqualTo("jane@example.com");
        assertThat(email.subject()).isEqualTo("Reset your password");
        assertThat(email.html()).contains("href=\"http://shop/reset-password?token=abc_123\"", "Choose a new password");
        assertThat(email.text()).contains("Hi Jane", "expires in 30 minutes",
                "Choose a new password: http://shop/reset-password?token=abc_123");
    }

    private static EmailRenderer renderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new EmailRenderer(engine, new EmailProperties("no-reply@shop.test", "Test Shop", "http://shop/{orderId}", ""));
    }
}
