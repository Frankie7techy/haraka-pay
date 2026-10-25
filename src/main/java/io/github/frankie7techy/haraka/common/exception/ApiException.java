package io.github.frankie7techy.haraka.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Base class for business-rule violations. Subclasses declare the HTTP status; the
 * API layer converts them to RFC 7807 problem responses via {@code GlobalExceptionHandler}.
 */
@ResponseStatus
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
