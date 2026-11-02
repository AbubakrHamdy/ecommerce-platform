package com.abubakr.ecommerceplatform.controller;

import com.abubakr.ecommerceplatform.dto.user.AddressRequest;
import com.abubakr.ecommerceplatform.dto.user.AddressResponse;
import com.abubakr.ecommerceplatform.security.CurrentUserId;
import com.abubakr.ecommerceplatform.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "The signed-in user's address book")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "List my addresses")
    public List<AddressResponse> list(@CurrentUserId Long userId) {
        return addressService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an address (the first one becomes the default)")
    public AddressResponse create(@CurrentUserId Long userId, @Valid @RequestBody AddressRequest request) {
        return addressService.create(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an address; isDefault=true moves the default here")
    public AddressResponse update(@CurrentUserId Long userId, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an address (past orders keep their own copy)")
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        addressService.delete(userId, id);
    }
}
