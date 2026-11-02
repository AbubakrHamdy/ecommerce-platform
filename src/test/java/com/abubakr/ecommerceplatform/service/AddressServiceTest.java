package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.dto.user.AddressRequest;
import com.abubakr.ecommerceplatform.entity.Address;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.exception.ConflictException;
import com.abubakr.ecommerceplatform.exception.ResourceNotFoundException;
import com.abubakr.ecommerceplatform.mapper.AddressMapperImpl;
import com.abubakr.ecommerceplatform.repository.AddressRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final Long USER_ID = 2L;

    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;

    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(addressRepository, userRepository, new AddressMapperImpl());
    }

    @Test
    void create_firstAddress_becomesDefaultEvenIfNotRequested() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(USER_ID, request(null));

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(saved.capture());
        assertThat(saved.getValue().isDefault()).isTrue();
    }

    @Test
    void create_asDefault_clearsThePreviousDefaultFirst() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(2L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(USER_ID, request(true));

        verify(addressRepository).clearDefault(USER_ID);
    }

    @Test
    void create_notDefault_leavesExistingDefaultAlone() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(USER_ID, request(false)).isDefault()).isFalse();
        verify(addressRepository, never()).clearDefault(any());
    }

    @Test
    void create_beyondTheLimit_isRejected() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn((long) AddressService.MAX_ADDRESSES_PER_USER);

        assertThatThrownBy(() -> service.create(USER_ID, request(false))).isInstanceOf(ConflictException.class);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void update_someoneElsesAddress_isNotFound() {
        when(addressRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 99L, request(true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found");
    }

    @Test
    void update_cannotRemoveTheDefaultFlagDirectly() {
        Address current = address(1L, true);
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(current));

        service.update(USER_ID, 1L, request(false));

        assertThat(current.isDefault()).isTrue();
    }

    @Test
    void delete_defaultAddress_promotesTheOldestRemainingOne() {
        Address deleted = address(1L, true);
        Address remaining = address(2L, false);
        when(addressRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(deleted));
        when(addressRepository.findFirstByUserIdOrderByIdAsc(USER_ID)).thenReturn(Optional.of(remaining));

        service.delete(USER_ID, 1L);

        verify(addressRepository).delete(deleted);
        assertThat(remaining.isDefault()).isTrue();
    }

    private static AddressRequest request(Boolean isDefault) {
        return new AddressRequest("1 Nile St", "Cairo", null, "11511", "Egypt", isDefault);
    }

    private static Address address(Long id, boolean isDefault) {
        Address address = Address.builder()
                .user(User.builder().build()).street("x").city("x").postalCode("x").country("x").isDefault(isDefault)
                .build();
        address.setId(id);
        return address;
    }
}
