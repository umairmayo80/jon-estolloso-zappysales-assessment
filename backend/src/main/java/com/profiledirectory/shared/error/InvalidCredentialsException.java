package com.profiledirectory.shared.error;

import org.springframework.http.HttpStatus;

/** Deliberately generic so login responses do not reveal account existence or status. */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
