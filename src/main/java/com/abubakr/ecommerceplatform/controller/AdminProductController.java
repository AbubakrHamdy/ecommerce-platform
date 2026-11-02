package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductCreateRequest;
import com.abubakr.ecommerceplatform.dto.product.ProductImageResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductSearchCriteria;
import com.abubakr.ecommerceplatform.dto.product.ProductSummaryResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductUpdateRequest;
import com.abubakr.ecommerceplatform.service.ProductImageService;
import com.abubakr.ecommerceplatform.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Products")
public class AdminProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    @GetMapping
    @Operation(summary = "Search all products, including archived ones")
    public PageResponse<ProductSummaryResponse> search(
            @Valid @ParameterObject ProductSearchCriteria criteria,
            @Parameter(description = "true = active only, false = archived only, omit = both")
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return productService.search(criteria, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any product, including archived ones")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product")
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a product (hidden from the store, kept for order history)")
    public void archive(@PathVariable Long id) {
        productService.archive(id);
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a product image (JPEG, PNG or WebP, max 5 MB; the first image becomes primary)")
    public ProductImageResponse uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return productImageService.upload(id, file);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a product image (the next image becomes primary if needed)")
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.delete(id, imageId);
    }
}
