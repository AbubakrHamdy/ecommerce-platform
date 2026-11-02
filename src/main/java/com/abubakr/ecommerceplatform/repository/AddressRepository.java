package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    Optional<Address> findFirstByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Query("update Address a set a.isDefault = false where a.user.id = :userId and a.isDefault = true")
    int clearDefault(@Param("userId") Long userId);
}
