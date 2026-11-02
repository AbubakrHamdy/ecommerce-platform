package com.abubakr.ecommerceplatform.exception;

// The provider's API failed or was unreachable; maps to 502 so the client knows to retry later.
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
