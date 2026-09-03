package com.profiledirectory.auth.application;

import com.profiledirectory.auth.domain.AdminAccount;
import java.security.Principal;
import java.util.UUID;

public record AdminPrincipal(UUID id, String email, String displayName, String role) implements Principal {
    public static AdminPrincipal from(AdminAccount account) {
        return new AdminPrincipal(account.getId(), account.getEmail(), account.getDisplayName(), account.getRole().name());
    }

    @Override
    public String getName() {
        return email;
    }

    @Override
    public String toString() {
        return "AdminPrincipal[redacted]";
    }
}
