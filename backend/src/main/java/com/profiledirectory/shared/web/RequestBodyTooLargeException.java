package com.profiledirectory.shared.web;

import java.io.IOException;

/** Raised when a request body exceeds the application-owned API limit. */
public class RequestBodyTooLargeException extends IOException {
    public RequestBodyTooLargeException() {
        super("Request body exceeds the configured limit");
    }
}
