package com.abubakr.ecommerceplatform.mapper;

import com.abubakr.ecommerceplatform.dto.payment.PaymentResponse;
import com.abubakr.ecommerceplatform.dto.payment.PaymentSessionResponse;
import com.abubakr.ecommerceplatform.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "paymentId", source = "id")
    PaymentSessionResponse toSessionResponse(Payment payment);
}
