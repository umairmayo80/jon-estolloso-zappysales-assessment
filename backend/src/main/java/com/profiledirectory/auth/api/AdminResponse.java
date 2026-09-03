package com.profiledirectory.auth.api;

import com.profiledirectory.auth.domain.AdminAccount;
import java.util.UUID;

public record AdminResponse(UUID id, String email, String displayName, String role) {
    public static AdminResponse from(AdminAccount account) {
        return new AdminResponse(account.getId(), account.getEmail(), account.getDisplayName(), account.getRole().name());
    }

    @Override
    public String toString() {
        return "AdminResponse[redacted]";
    }
}
