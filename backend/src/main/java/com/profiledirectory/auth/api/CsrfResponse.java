package com.profiledirectory.auth.api;

public record CsrfResponse(String token) {
    @Override
    public String toString() {
        return "CsrfResponse[token=redacted]";
    }
}
