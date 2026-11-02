package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductSearchCriteria;
import com.abubakr.ecommerceplatform.dto.product.ProductSummaryResponse;
import com.abubakr.ecommerceplatform.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Products", description = "Browse and search the public catalog")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Search active products",
            description = "Filters are optional and combine with AND. Sortable fields: createdAt, name, price. Max page size 100.")
    public PageResponse<ProductSummaryResponse> search(
            @Valid @ParameterObject ProductSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return productService.search(criteria, true, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active product with its images")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getActiveProduct(id);
    }
}
