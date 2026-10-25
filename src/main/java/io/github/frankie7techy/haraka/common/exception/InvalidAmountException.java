package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Amount violates the configured money policy. */
public class InvalidAmountException extends ApiException {
    public InvalidAmountException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AMOUNT", message);
    }
}
