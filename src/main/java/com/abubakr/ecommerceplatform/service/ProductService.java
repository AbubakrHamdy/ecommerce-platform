package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.CacheNames;
import com.abubakr.ecommerceplatform.dto.common.PageResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductCreateRequest;
import com.abubakr.ecommerceplatform.dto.product.ProductResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductSearchCriteria;
import com.abubakr.ecommerceplatform.dto.product.ProductSummaryResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductUpdateRequest;
import com.abubakr.ecommerceplatform.entity.Category;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.ProductMapper;
import com.abubakr.ecommerceplatform.repository.CategoryRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import com.abubakr.ecommerceplatform.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("price", "name", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, Boolean active, Pageable pageable) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        Set<Long> categoryIds = criteria.categoryId() == null
                ? null
                : CategoryService.subtreeIds(categoryService.findAll(), criteria.categoryId());

        Specification<Product> filters = ProductSpecifications.matching(
                criteria.search(), categoryIds, criteria.minPrice(), criteria.maxPrice(), active);

        return PageResponse.of(productRepository.findAll(filters, Pageables.withAllowedSort(pageable, SORTABLE_FIELDS))
                .map(productMapper::toSummary));
    }

    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getActiveProduct(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return productMapper.toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = productMapper.toEntity(request);
        product.setCategory(findCategory(request.categoryId()));
        return productMapper.toResponse(productRepository.save(product));
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findProduct(id);
        productMapper.updateEntity(request, product);
        product.setCategory(findCategory(request.categoryId()));
        return productMapper.toResponse(product);
    }

    // Soft delete: past orders reference the product, so the row must survive.
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public void archive(Long id) {
        findProduct(id).setActive(false);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }
}
