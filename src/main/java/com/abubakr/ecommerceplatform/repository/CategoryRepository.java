package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentId(Long parentId);

    Optional<Category> findFirstByNameIgnoreCaseAndParentIsNull(String name);

    Optional<Category> findFirstByNameIgnoreCaseAndParentId(String name, Long parentId);
}
