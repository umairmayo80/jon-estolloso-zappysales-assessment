package com.profiledirectory.shared.error;

import org.springframework.http.HttpStatus;

public class PreconditionFailedException extends ApiException {
    public PreconditionFailedException(String detail) { super(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION", detail); }
}
