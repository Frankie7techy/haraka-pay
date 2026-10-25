package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Login or bearer token authentication failed. */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }
}
