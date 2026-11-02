package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.product.ProductCreateRequest;
import com.abubakr.ecommerceplatform.dto.product.ProductImageResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductSummaryResponse;
import com.abubakr.ecommerceplatform.dto.product.ProductUpdateRequest;
import com.abubakr.ecommerceplatform.entity.Product;
import com.abubakr.ecommerceplatform.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = CategoryMapper.class)
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "thumbnailUrl", expression = "java(thumbnailUrl(product))")
    @Mapping(target = "categoryName", source = "category.name")
    ProductSummaryResponse toSummary(Product product);

    @Mapping(target = "isPrimary", source = "primary")
    ProductImageResponse toImageResponse(ProductImage image);

    @Mapping(target = "active", source = "active", defaultValue = "true")
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    Product toEntity(ProductCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "reviews", ignore = true)
    void updateEntity(ProductUpdateRequest request, @MappingTarget Product product);

    default String thumbnailUrl(Product product) {
        return product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getUrl)
                .orElse(null);
    }
}
