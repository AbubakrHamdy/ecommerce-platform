package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

final class Pageables {

    private Pageables() {
    }

    // Only whitelisted fields can be sorted on, and "id" is appended so rows with equal values keep a stable order.
    static Pageable withAllowedSort(Pageable pageable, Set<String> allowedFields) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedFields.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed fields: "
                        + String.join(", ", new TreeSet<>(allowedFields)));
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
    }
}
