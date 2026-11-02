package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.product.ProductCreateRequest;
import com.abubakr.ecommerceplatform.dto.product.ProductSearchCriteria;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.ProductMapper;
import com.abubakr.ecommerceplatform.repository.CategoryRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final ProductSearchCriteria NO_FILTERS = new ProductSearchCriteria(null, null, null, null);

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryService categoryService;
    @Mock private ProductMapper productMapper;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, categoryService, productMapper);
    }

    @Test
    void search_sortingByAnUnknownField_isRejectedBeforeQuerying() {
        Pageable bySecretColumn = PageRequest.of(0, 20, Sort.by("password"));

        assertThatThrownBy(() -> service.search(NO_FILTERS, true, bySecretColumn))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'password'");
        verifyNoInteractions(productRepository);
    }

    @Test
    void search_minPriceAboveMaxPrice_isRejected() {
        ProductSearchCriteria inverted = new ProductSearchCriteria(null, null, new BigDecimal("500"), new BigDecimal("100"));

        assertThatThrownBy(() -> service.search(inverted, true, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_addsIdAsTieBreakerSoPagesStayStable() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search(NO_FILTERS, true, PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "price")));

        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), used.capture());
        assertThat(used.getValue().getPageNumber()).isEqualTo(2);
        assertThat(used.getValue().getSort().stream().map(Sort.Order::getProperty)).containsExactly("price", "id");
    }

    @Test
    void getActiveProduct_archivedOrMissing_isNotFound() {
        when(productRepository.findByIdAndActiveTrue(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveProduct(6L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void create_withUnknownCategory_isNotFoundAndSavesNothing() {
        ProductCreateRequest request = new ProductCreateRequest("Phone", null, new BigDecimal("199.99"), 5, 99L, true);
        when(productMapper.toEntity(request)).thenReturn(Product.builder().name("Phone").build());
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
        verify(productRepository, never()).save(any());
    }

    @Test
    void archive_hidesProductWithoutDeletingIt() {
        Product product = Product.builder().name("Phone").active(true).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        service.archive(1L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository, never()).delete(any(Product.class));
    }
}
