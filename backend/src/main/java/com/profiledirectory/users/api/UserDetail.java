package com.profiledirectory.users.api;

import com.profiledirectory.addresses.api.AddressResponse;
import com.profiledirectory.users.domain.UserProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDetail(
        UUID id,
        String email,
        String firstName,
        String lastName,
        boolean deleted,
        Instant deletedAt,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<AddressResponse> addresses) {
    public static UserDetail from(UserProfile profile, List<AddressResponse> addresses) {
        return new UserDetail(
                profile.getId(), profile.getEmail(), profile.getFirstName(), profile.getLastName(), profile.isDeleted(),
                profile.getDeletedAt(), profile.getVersion(), profile.getCreatedAt(), profile.getUpdatedAt(), addresses);
    }

    @Override
    public String toString() {
        return "UserDetail[redacted]";
    }
}
