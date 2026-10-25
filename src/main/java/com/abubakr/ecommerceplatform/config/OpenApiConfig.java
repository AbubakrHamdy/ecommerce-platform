package com.abubakr.ecommerceplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("E-Commerce Platform API")
                .description("REST API for the e-commerce platform: auth, catalog, cart, orders, payments and admin stats.")
                .version("v1"));
    }
}
