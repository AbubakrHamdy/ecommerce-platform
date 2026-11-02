package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.entity.ProductImage;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.ProductMapper;
import com.abubakr.ecommerceplatform.repository.ProductImageRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository imageRepository;
    @Mock private FileStorageService storage;
    @Mock private ProductMapper productMapper;

    private ProductImageService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductImageService(productRepository, imageRepository, storage, productMapper);
        product = Product.builder().name("Laptop").build();
        product.setId(5L);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void upload_firstImage_becomesPrimaryAndIsStoredUnderARandomKey() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(storage.upload(anyString(), any(), eq("image/png"))).thenReturn("http://storage/x.png");
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(5L, new MockMultipartFile("file", "../../etc/passwd.png", "image/png", PNG));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), eq(PNG), eq("image/png"));
        assertThat(key.getValue()).matches("products/5/[0-9a-f-]{36}\\.png");

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().isPrimary()).isTrue();
        assertThat(saved.getValue().getDisplayOrder()).isZero();
        assertThat(saved.getValue().getStorageKey()).isEqualTo(key.getValue());
    }

    @Test
    void upload_laterImages_areNotPrimaryAndGoToTheEnd() {
        product.getImages().add(image(1L, true, 0, "products/5/a.png"));
        product.getImages().add(image(2L, false, 3, "products/5/b.png"));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(storage.upload(anyString(), any(), anyString())).thenReturn("http://storage/c.png");
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(5L, new MockMultipartFile("file", "c.png", "image/png", PNG));

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().isPrimary()).isFalse();
        assertThat(saved.getValue().getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void upload_executableRenamedToJpg_isRejectedAndNothingIsStored() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

        assertThatThrownBy(() -> service.upload(5L, new MockMultipartFile("file", "cat.jpg", "image/jpeg", exe)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only JPEG, PNG and WebP images are allowed");
        verifyNoInteractions(storage, imageRepository);
    }

    @Test
    void upload_whenProductAlreadyHasTenImages_isRejected() {
        for (long i = 1; i <= ProductImageService.MAX_IMAGES_PER_PRODUCT; i++) {
            product.getImages().add(image(i, i == 1, (int) i, null));
        }
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.upload(5L, new MockMultipartFile("file", "x.png", "image/png", PNG)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void upload_whenTransactionRollsBack_storedFileIsDeleted() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(storage.upload(anyString(), any(), anyString())).thenReturn("http://storage/x.png");
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(5L, new MockMultipartFile("file", "x.png", "image/png", PNG));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), any(), anyString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storage).delete(key.getValue());
    }

    @Test
    void delete_primaryImage_promotesNextAndRemovesFileOnlyAfterCommit() {
        ProductImage first = image(1L, true, 0, "products/5/a.png");
        ProductImage second = image(2L, false, 1, "products/5/b.png");
        product.getImages().add(first);
        product.getImages().add(second);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        service.delete(5L, 1L);

        assertThat(product.getImages()).containsExactly(second);
        assertThat(second.isPrimary()).isTrue();
        verify(storage, never()).delete(anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("products/5/a.png");
    }

    @Test
    void delete_imageOfAnotherProduct_isNotFound() {
        product.getImages().add(image(1L, true, 0, null));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.delete(5L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Image not found");
    }

    private ProductImage image(Long id, boolean primary, int order, String key) {
        ProductImage image = ProductImage.builder()
                .product(product).url("http://storage/" + id).storageKey(key).isPrimary(primary).displayOrder(order)
                .build();
        image.setId(id);
        return image;
    }
}
