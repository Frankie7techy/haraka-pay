package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Requested resource does not exist (or the caller may not know it exists). */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
