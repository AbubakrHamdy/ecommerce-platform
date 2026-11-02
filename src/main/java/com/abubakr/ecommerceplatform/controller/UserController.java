package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.user.UserResponse;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user's profile")
    public UserResponse me(@CurrentUserId Long userId) {
        return userService.getById(userId);
    }
}
