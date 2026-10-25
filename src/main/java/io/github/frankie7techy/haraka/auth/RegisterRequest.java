package io.github.frankie7techy.haraka.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fields kept as primitives-with-validation so bean validation errors surface before any
 * service code runs.
 */
public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "fullName is required")
        @Size(min = 2, max = 100, message = "fullName must be 2-100 characters")
        String fullName,

        @NotBlank(message = "password is required")
        @Size(min = 10, max = 100, message = "password must be 10-100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "password must mix upper case, lower case and digits")
        String password) {
}
