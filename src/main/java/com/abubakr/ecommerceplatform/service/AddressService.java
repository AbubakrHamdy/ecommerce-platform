package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.user.AddressRequest;
import com.abubakr.ecommerceplatform.dto.user.AddressResponse;
import com.abubakr.ecommerceplatform.entity.Address;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.AddressMapper;
import com.abubakr.ecommerceplatform.repository.AddressRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new ConflictException("You can save at most " + MAX_ADDRESSES_PER_USER + " addresses");
        }
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            addressRepository.clearDefault(userId);
        }
        Address address = Address.builder()
                .user(userRepository.getReferenceById(userId))
                .street(request.street().trim())
                .city(request.city().trim())
                .state(request.state() == null ? null : request.state().trim())
                .postalCode(request.postalCode().trim())
                .country(request.country().trim())
                .isDefault(makeDefault)
                .build();
        return addressMapper.toResponse(addressRepository.save(address));
    }

    // A default can only be moved to another address, never removed, so a user with addresses always has one.
    @Transactional
    public AddressResponse update(Long userId, Long id, AddressRequest request) {
        Address address = findOwn(userId, id);
        address.setStreet(request.street().trim());
        address.setCity(request.city().trim());
        address.setState(request.state() == null ? null : request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(request.country().trim());
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            addressRepository.clearDefault(userId);
            address.setDefault(true);
        }
        return addressMapper.toResponse(address);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Address address = findOwn(userId, id);
        addressRepository.delete(address);
        if (address.isDefault()) {
            addressRepository.flush();
            addressRepository.findFirstByUserIdOrderByIdAsc(userId)
                    .ifPresent(next -> next.setDefault(true));
        }
    }

    private Address findOwn(Long userId, Long id) {
        return addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }
}
