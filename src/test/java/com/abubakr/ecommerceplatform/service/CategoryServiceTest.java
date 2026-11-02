package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.category.CategoryRequest;
import com.abubakr.ecommerceplatform.dto.category.CategoryResponse;
import com.abubakr.ecommerceplatform.entity.Category;
import com.abubakr.ecommerceplatform.exception.BadRequestException;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.DuplicateResourceException;
import com.abubakr.ecommerceplatform.mapper.CategoryMapperImpl;
import com.abubakr.ecommerceplatform.repository.CategoryRepository;
import com.abubakr.ecommerceplatform.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;

    private CategoryService service;

    // electronics(1) -> laptops(2) -> gaming(3); clothing(4) is unrelated
    private Category electronics;
    private Category laptops;
    private Category gaming;
    private Category clothing;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, productRepository, new CategoryMapperImpl());
        electronics = category(1L, "Electronics", null);
        laptops = category(2L, "Laptops", electronics);
        gaming = category(3L, "Gaming", laptops);
        clothing = category(4L, "Clothing", null);
    }

    @Test
    void update_categoryAsItsOwnParent_isRejected() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));

        assertThatThrownBy(() -> service.update(1L, new CategoryRequest("Electronics", 1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A category cannot be its own parent");
    }

    @Test
    void update_movingUnderOwnGrandchild_isRejectedToPreventALoop() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(gaming));

        assertThatThrownBy(() -> service.update(1L, new CategoryRequest("Electronics", 3L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A category cannot be moved under one of its own subcategories");
    }

    @Test
    void update_movingUnderUnrelatedCategory_isAllowed() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(laptops));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(clothing));
        when(categoryRepository.findFirstByNameIgnoreCaseAndParentId("Laptops", 4L)).thenReturn(Optional.empty());

        CategoryResponse response = service.update(2L, new CategoryRequest("Laptops", 4L));

        assertThat(response.parentId()).isEqualTo(4L);
        assertThat(response.parentName()).isEqualTo("Clothing");
    }

    @Test
    void update_keepingItsOwnName_isNotADuplicate() {
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(clothing));
        when(categoryRepository.findFirstByNameIgnoreCaseAndParentIsNull("Clothing")).thenReturn(Optional.of(clothing));

        assertThat(service.update(4L, new CategoryRequest(" Clothing ", null)).name()).isEqualTo("Clothing");
    }

    @Test
    void create_nameAlreadyUsedBySibling_isRejected() {
        when(categoryRepository.findFirstByNameIgnoreCaseAndParentIsNull("clothing")).thenReturn(Optional.of(clothing));

        assertThatThrownBy(() -> service.create(new CategoryRequest("clothing", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void delete_categoryWithSubcategories_isBlocked() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_categoryWithProducts_isBlocked() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(gaming));
        when(categoryRepository.existsByParentId(3L)).thenReturn(false);
        when(productRepository.existsByCategoryId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_emptyCategory_isDeleted() {
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(clothing));
        when(categoryRepository.existsByParentId(4L)).thenReturn(false);
        when(productRepository.existsByCategoryId(4L)).thenReturn(false);

        service.delete(4L);

        verify(categoryRepository).delete(clothing);
    }

    @Test
    void subtreeIds_includesAllDescendantsButNotSiblings() {
        List<CategoryResponse> all = List.of(
                new CategoryResponse(1L, "Electronics", null, null),
                new CategoryResponse(2L, "Laptops", 1L, "Electronics"),
                new CategoryResponse(3L, "Gaming", 2L, "Laptops"),
                new CategoryResponse(4L, "Clothing", null, null));

        assertThat(CategoryService.subtreeIds(all, 1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(CategoryService.subtreeIds(all, 2L)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(CategoryService.subtreeIds(all, 4L)).containsExactly(4L);
    }

    private static Category category(Long id, String name, Category parent) {
        Category category = Category.builder().name(name).parent(parent).build();
        category.setId(id);
        return category;
    }
}
