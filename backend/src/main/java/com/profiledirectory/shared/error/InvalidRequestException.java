package com.profiledirectory.shared.error;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends ApiException {
    public InvalidRequestException(String detail) { super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail); }
}
