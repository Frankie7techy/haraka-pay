package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Email is already registered. */
public class DuplicateEmailException extends ApiException {
    public DuplicateEmailException(String message) {
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", message);
    }
}
