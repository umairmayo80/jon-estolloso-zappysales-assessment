package com.profiledirectory.shared.error;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends ApiException {
    public UnauthenticatedException() { super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"); }
    public UnauthenticatedException(String detail) { super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", detail); }
}
