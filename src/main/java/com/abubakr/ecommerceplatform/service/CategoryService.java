package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.CacheNames;
import com.abubakr.ecommerceplatform.dto.category.CategoryRequest;
import com.abubakr.ecommerceplatform.dto.category.CategoryResponse;
import com.abubakr.ecommerceplatform.entity.Category;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.DuplicateResourceException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.CategoryMapper;
import com.abubakr.ecommerceplatform.repository.CategoryRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        Category parent = resolveParent(request.parentId());
        ensureNameIsFreeAmongSiblings(name, parent, null);
        Category saved = categoryRepository.save(Category.builder().name(name).parent(parent).build());
        return categoryMapper.toResponse(saved);
    }

    // Cached products embed their category name, so a rename must clear them too.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, allEntries = true)
    })
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        Category parent = resolveParent(request.parentId());
        ensureNoCycle(category, parent);
        ensureNameIsFreeAmongSiblings(name, parent, id);
        category.setName(name);
        category.setParent(parent);
        return categoryMapper.toResponse(category);
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("This category has subcategories; move or delete them first");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("This category still has products (including archived ones); move them first");
        }
        categoryRepository.delete(category);
    }

    public static Set<Long> subtreeIds(List<CategoryResponse> categories, Long rootId) {
        Map<Long, List<Long>> childrenByParent = categories.stream()
                .filter(category -> category.parentId() != null)
                .collect(groupingBy(CategoryResponse::parentId, mapping(CategoryResponse::id, toList())));

        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(rootId));
        while (!pending.isEmpty()) {
            Long id = pending.pop();
            if (ids.add(id)) {
                pending.addAll(childrenByParent.getOrDefault(id, List.of()));
            }
        }
        return ids;
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
    }

    private void ensureNoCycle(Category category, Category newParent) {
        if (newParent == null) {
            return;
        }
        if (newParent.getId().equals(category.getId())) {
            throw new BadRequestException("A category cannot be its own parent");
        }
        for (Category ancestor = newParent.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(category.getId())) {
                throw new BadRequestException("A category cannot be moved under one of its own subcategories");
            }
        }
    }

    private void ensureNameIsFreeAmongSiblings(String name, Category parent, Long excludeId) {
        Optional<Category> existing = parent == null
                ? categoryRepository.findFirstByNameIgnoreCaseAndParentIsNull(name)
                : categoryRepository.findFirstByNameIgnoreCaseAndParentId(name, parent.getId());
        existing.filter(other -> !other.getId().equals(excludeId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("A category named '" + name + "' already exists at this level");
                });
    }
}
