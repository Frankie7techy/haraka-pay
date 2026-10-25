package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** A transfer violates a business rule that is not about funds (self transfer, currency). */
public class InvalidTransferException extends ApiException {
    public InvalidTransferException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TRANSFER", message);
    }
}
