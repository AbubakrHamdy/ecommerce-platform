package com.abubakr.ecommerceplatform.email;

import com.abubakr.ecommerceplatform.enums.OrderStatus;
import com.abubakr.ecommerceplatform.event.OrderStatusChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEmailListenerTest {

    private static final OrderStatusChanged SHIPPED = OrderStatusChanged.to(12L, OrderStatus.SHIPPED);
    private static final EmailMessage EMAIL = new EmailMessage("jane@example.com", "Shipped", "<p>x</p>", "x");

    @Mock private OrderEmails orderEmails;
    @Mock private EmailSender emailSender;
    @InjectMocks private OrderEmailListener listener;

    @Test
    void sendsTheEmailBuiltForTheStatusChange() {
        when(orderEmails.forStatusChange(SHIPPED)).thenReturn(Optional.of(EMAIL));

        listener.onOrderStatusChanged(SHIPPED);

        verify(emailSender).send(EMAIL);
    }

    @Test
    void statusesWithoutAnEmail_sendNothing() {
        OrderStatusChanged processing = OrderStatusChanged.to(12L, OrderStatus.PROCESSING);
        when(orderEmails.forStatusChange(processing)).thenReturn(Optional.empty());

        listener.onOrderStatusChanged(processing);

        verifyNoInteractions(emailSender);
    }

    @Test
    void mailFailureAfterRetries_isLoggedNotThrown() {
        when(orderEmails.forStatusChange(SHIPPED)).thenReturn(Optional.of(EMAIL));
        doThrow(new MailSendException("down")).when(emailSender).send(any());

        assertThatCode(() -> listener.onOrderStatusChanged(SHIPPED)).doesNotThrowAnyException();
    }
}
