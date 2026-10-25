package io.github.frankie7techy.haraka.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every failure leaves the API as an RFC 7807 problem document carrying a stable
 * machine-readable {@code code}, so clients can branch on semantics instead of strings.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(problem(exception.getStatus(),
                exception.getStatus().getReasonPhrase(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Bad Request", "VALIDATION_FAILED",
                "Request validation failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerValidation(HandlerMethodValidationException exception) {
        String detail = "Request validation failed: " + String.join("; ",
                exception.getAllErrors().stream().map(Object::toString).toList());
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "VALIDATION_FAILED", detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "BAD_REQUEST", "Malformed request: " + exception.getClass().getSimpleName()));
    }

    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    public ResponseEntity<ProblemDetail> handleHeaderBinding(
            org.springframework.web.bind.ServletRequestBindingException exception) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "BAD_REQUEST", "A required header or parameter is missing or invalid"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(problem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "METHOD_NOT_ALLOWED",
                        "HTTP method not supported on this resource"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, "Conflict", "DATA_CONFLICT",
                        "The request conflicts with existing data"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return ResponseEntity.status(exception.getStatusCode())
                .body(problem(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                        status == null ? "Error" : status.getReasonPhrase(), "REQUEST_REJECTED",
                        exception.getReason() == null ? "Request rejected" : exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "INTERNAL_ERROR",
                        "An unexpected error occurred"));
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
