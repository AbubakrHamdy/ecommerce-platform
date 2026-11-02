package com.abubakr.ecommerceplatform.repository;

import com.abubakr.ecommerceplatform.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against the real PostgreSQL from docker-compose: row locking is what is being tested, so no mocks.
@SpringBootTest
@ActiveProfiles("dev")
class StockReservationIntegrationTest {

    private static final int STOCK = 3;
    private static final int BUYERS = 8;

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void manyBuyersAtOnce_neverOversell() throws Exception {
        Product product = productRepository.save(Product.builder()
                .name("Concurrency test item")
                .price(BigDecimal.TEN)
                .stock(STOCK)
                .active(true)
                .category(categoryRepository.findAll().getFirst())
                .build());
        Long productId = product.getId();

        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < BUYERS; i++) {
                attempts.add(pool.submit(() -> {
                    startTogether.await();
                    return transactionTemplate.execute(status -> productRepository.reserveStock(productId, 1));
                }));
            }
            startTogether.countDown();

            int succeeded = 0;
            for (Future<Integer> attempt : attempts) {
                succeeded += attempt.get();
            }

            assertThat(succeeded).isEqualTo(STOCK);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
        } finally {
            pool.shutdownNow();
            productRepository.deleteById(productId);
        }
    }
}
