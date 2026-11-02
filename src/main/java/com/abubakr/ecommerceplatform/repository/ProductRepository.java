package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByCategoryId(Long categoryId);

    Optional<Product> findByIdAndActiveTrue(Long id);
}
