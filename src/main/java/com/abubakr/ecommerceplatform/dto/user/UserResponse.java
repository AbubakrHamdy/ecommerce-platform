package com.abubakr.ecommerceplatform.dto.user;

import com.abubakr.ecommerceplatform.enums.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        Instant createdAt
) {}
