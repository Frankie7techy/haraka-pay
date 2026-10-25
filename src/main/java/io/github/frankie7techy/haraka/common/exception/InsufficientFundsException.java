package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** The wallet cannot fund the requested operation. */
public class InsufficientFundsException extends ApiException {
    public InsufficientFundsException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", message);
    }
}
