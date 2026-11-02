package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.CacheNames;
import com.abubakr.ecommerceplatform.dto.product.ProductImageResponse;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.entity.ProductImage;
import com.abubakr.ecommerceplatform.enums.ImageType;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.ProductMapper;
import com.abubakr.ecommerceplatform.repository.ProductImageRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    static final int MAX_IMAGES_PER_PRODUCT = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final FileStorageService storage;
    private final ProductMapper productMapper;

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    @Transactional
    public ProductImageResponse upload(Long productId, MultipartFile file) {
        Product product = findProduct(productId);
        if (product.getImages().size() >= MAX_IMAGES_PER_PRODUCT) {
            throw new ConflictException("A product can have at most " + MAX_IMAGES_PER_PRODUCT + " images");
        }

        byte[] content = readContent(file);
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new BadRequestException("Only JPEG, PNG and WebP images are allowed"));

        String key = "products/%d/%s.%s".formatted(productId, UUID.randomUUID(), type.extension());
        String url = storage.upload(key, content, type.contentType());
        deleteFileIfTransactionRollsBack(key);

        int nextDisplayOrder = product.getImages().stream()
                .mapToInt(ProductImage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        ProductImage image = imageRepository.save(ProductImage.builder()
                .product(product)
                .url(url)
                .storageKey(key)
                .isPrimary(product.getImages().isEmpty())
                .displayOrder(nextDisplayOrder)
                .build());
        product.getImages().add(image);
        return productMapper.toImageResponse(image);
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    @Transactional
    public void delete(Long productId, Long imageId) {
        Product product = findProduct(productId);
        ProductImage image = product.getImages().stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        product.getImages().remove(image);
        if (image.isPrimary()) {
            product.getImages().stream().findFirst().ifPresent(next -> next.setPrimary(true));
        }
        if (image.getStorageKey() != null) {
            deleteFileAfterCommit(image.getStorageKey());
        }
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private static byte[] readContent(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read");
        }
    }

    // The file is stored before the database row exists; if the transaction fails, remove the orphaned file.
    private void deleteFileIfTransactionRollsBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.delete(key);
                }
            }
        });
    }

    // Only delete the file once the row removal is committed, so a rollback never leaves a broken image.
    private void deleteFileAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }
}
