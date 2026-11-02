package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(String search, Collection<Long> categoryIds,
                                                  BigDecimal minPrice, BigDecimal maxPrice, Boolean active) {
        List<Specification<Product>> filters = new ArrayList<>();
        if (active != null) filters.add(hasActive(active));
        if (StringUtils.hasText(search)) filters.add(nameOrDescriptionContains(search));
        if (categoryIds != null) filters.add(categoryIn(categoryIds));
        if (minPrice != null) filters.add(priceAtLeast(minPrice));
        if (maxPrice != null) filters.add(priceAtMost(maxPrice));
        return Specification.allOf(filters);
    }

    static Specification<Product> hasActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    static Specification<Product> nameOrDescriptionContains(String term) {
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE));
    }

    static Specification<Product> categoryIn(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    // So a search for "50%" matches the literal text instead of acting as a wildcard.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
