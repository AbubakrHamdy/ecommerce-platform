package com.abubakr.ecommerceplatform.dto.stats;

import com.abubakr.ecommerceplatform.enums.TopProductRanking;

import java.time.LocalDate;
import java.util.List;

public record TopProductsResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        TopProductRanking rankedBy,
        List<TopProductResponse> products
) {}
