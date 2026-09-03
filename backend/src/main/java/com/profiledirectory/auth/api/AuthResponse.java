package com.profiledirectory.auth.api;

public record AuthResponse(AdminResponse admin) {
    @Override
    public String toString() {
        return "AuthResponse[admin=redacted]";
    }
}
