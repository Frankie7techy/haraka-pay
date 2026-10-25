package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Idempotency-Key header missing or malformed. */
public class IdempotencyKeyException extends ApiException {
    public IdempotencyKeyException(String message) {
        super(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", message);
    }
}
