package com.abubakr.ecommerceplatform.event;

// A payment a person must look at: money arrived for a closed order, or the amount didn't match.
public record PaymentNeedsReview(Long paymentId) {
}
