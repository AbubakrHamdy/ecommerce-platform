package com.abubakr.ecommerceplatform.dto.review;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt
) {}
