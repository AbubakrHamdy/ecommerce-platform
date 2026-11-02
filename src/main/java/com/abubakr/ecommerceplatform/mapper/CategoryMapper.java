package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.category.CategoryResponse;
import com.abubakr.ecommerceplatform.dto.category.CategorySummaryResponse;
import com.abubakr.ecommerceplatform.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CategoryMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    CategoryResponse toResponse(Category category);

    CategorySummaryResponse toSummary(Category category);
}
