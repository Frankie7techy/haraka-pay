package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** The same idempotency key was reused with a different payload, or replay raced in flight. */
public class IdempotencyException extends ApiException {
    public IdempotencyException(String message) {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", message);
    }
}
