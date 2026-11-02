package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByCategoryId(Long categoryId);

    Optional<Product> findByIdAndActiveTrue(Long id);

    // Check and decrement in one statement: concurrent buyers can never both take the last unit.
    @Modifying
    @Query("""
            update Product p set p.stock = p.stock - :quantity
            where p.id = :id and p.active = true and p.stock >= :quantity""")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);
}
