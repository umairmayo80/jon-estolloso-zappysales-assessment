package com.profiledirectory.addresses.api;

import com.profiledirectory.addresses.domain.Address;
import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        boolean primary,
        int displayOrder,
        boolean deleted,
        Instant deletedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(), address.getLabel(), address.getLine1(), address.getLine2(), address.getCity(),
                address.getRegion(), address.getPostalCode(), address.getCountryCode(), address.isPrimary(),
                address.getDisplayOrder(), address.isDeleted(), address.getDeletedAt(), address.getVersion(), address.getCreatedAt(), address.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "AddressResponse[redacted]";
    }
}
