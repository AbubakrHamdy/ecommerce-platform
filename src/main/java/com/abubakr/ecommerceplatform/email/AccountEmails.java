package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.event.PasswordResetRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountEmails {

    private final EmailRenderer renderer;

    public EmailMessage passwordReset(PasswordResetRequested event) {
        EmailContent content = EmailContent.builder()
                .headline("Reset your password")
                .paragraph("Hi " + event.firstName() + ", we received a request to reset your password.")
                .paragraph("The link below expires in " + Durations.humanize(event.validFor())
                        + " and can be used once. If you didn't ask for this, you can ignore this email;"
                        + " your password won't change.")
                .actionLabel("Choose a new password")
                .actionUrl(event.resetLink())
                .build();
        return renderer.render(event.email(), "Reset your password", content);
    }
}
