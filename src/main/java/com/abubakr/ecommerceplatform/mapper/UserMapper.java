package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.user.UserResponse;
import com.abubakr.ecommerceplatform.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
