package com.profiledirectory.shared.error;

import org.springframework.http.HttpStatus;

public class PreconditionRequiredException extends ApiException {
    public PreconditionRequiredException(String detail) { super(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", detail); }
}
