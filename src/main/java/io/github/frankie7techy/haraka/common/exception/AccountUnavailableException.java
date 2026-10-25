package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;

/** Wallet account exists but is not usable (frozen/closed), or caller does not own it. */
public class AccountUnavailableException extends ApiException {
    public AccountUnavailableException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
