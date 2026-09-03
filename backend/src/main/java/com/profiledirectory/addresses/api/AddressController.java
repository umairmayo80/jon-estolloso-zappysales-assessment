package com.profiledirectory.addresses.api;

import com.profiledirectory.addresses.application.AddressService;
import com.profiledirectory.auth.application.AdminPrincipal;
import com.profiledirectory.shared.web.EntityTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/addresses")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Addresses")
public class AddressController {
    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @PostMapping
    @Operation(summary = "Add an address to an active user")
    public ResponseEntity<AddressResponse> create(
            @PathVariable UUID userId,
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        AddressResponse address = addresses.create(userId, request, principal.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(EntityTag.forAddress(address.id(), address.version()))
                .body(address);
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Get an address and its strong ETag")
    public ResponseEntity<AddressResponse> get(@PathVariable UUID userId, @PathVariable UUID addressId) {
        AddressResponse address = addresses.get(userId, addressId);
        return ResponseEntity.ok().eTag(EntityTag.forAddress(address.id(), address.version())).body(address);
    }

    @PatchMapping("/{addressId}")
    @Operation(summary = "Update an address using a strong ETag precondition")
    public ResponseEntity<AddressResponse> update(
            @PathVariable UUID userId,
            @PathVariable UUID addressId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        AddressResponse address = addresses.update(userId, addressId, request, ifMatch, principal.id());
        return ResponseEntity.ok().eTag(EntityTag.forAddress(address.id(), address.version())).body(address);
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Soft-delete an address")
    public ResponseEntity<Void> delete(
            @PathVariable UUID userId,
            @PathVariable UUID addressId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.noContent().eTag(addresses.softDelete(userId, addressId, ifMatch, principal.id())).build();
    }

    @PostMapping("/{addressId}/restore")
    @Operation(summary = "Restore a soft-deleted address")
    public ResponseEntity<Void> restore(
            @PathVariable UUID userId,
            @PathVariable UUID addressId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.noContent().eTag(addresses.restore(userId, addressId, ifMatch, principal.id())).build();
    }
}
