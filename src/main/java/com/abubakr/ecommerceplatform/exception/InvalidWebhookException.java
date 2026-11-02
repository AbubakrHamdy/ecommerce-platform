package com.abubakr.ecommerceplatform.exception;

public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }

    public InvalidWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
