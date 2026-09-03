package com.profiledirectory.users.api;

import com.profiledirectory.users.domain.UserProfile;
import java.time.Instant;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        long addressCount,
        boolean deleted,
        long version,
        Instant updatedAt) {
    public static UserSummary from(UserProfile profile, long addressCount) {
        return new UserSummary(
                profile.getId(), profile.getEmail(), profile.getFirstName(), profile.getLastName(), addressCount,
                profile.isDeleted(), profile.getVersion(), profile.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "UserSummary[redacted]";
    }
}
