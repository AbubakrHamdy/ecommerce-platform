package com.abubakr.ecommerceplatform.security;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injects the signed-in user's id (the JWT "sub" claim) into a controller parameter.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(hidden = true)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface CurrentUserId {
}
