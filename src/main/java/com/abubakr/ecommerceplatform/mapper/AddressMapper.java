package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.user.AddressResponse;
import com.abubakr.ecommerceplatform.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AddressMapper {

    @Mapping(target = "isDefault", expression = "java(address.isDefault())")
    AddressResponse toResponse(Address address);
}
