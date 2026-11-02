package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Payment;
import com.abubakr.ecommerceplatform.enums.PaymentProvider;
import com.abubakr.ecommerceplatform.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<Payment> findByProviderAndProviderSessionId(PaymentProvider provider, String providerSessionId);

    // Returns 1 the first time an event is seen and 0 for any repeat, even when two deliveries arrive at once.
    @Modifying
    @Query(value = """
            insert into processed_webhook_events (provider, event_id, received_at)
            values (:provider, :eventId, now())
            on conflict (provider, event_id) do nothing""", nativeQuery = true)
    int markEventProcessed(@Param("provider") String provider, @Param("eventId") String eventId);
}
