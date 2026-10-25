package io.github.frankie7techy.haraka.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A P2P transfer instruction. Amounts are integer minor units (KES 1.00 == 100). */
public record NewTransferRequest(
        @NotBlank(message = "targetAccountNumber is required")
        @Pattern(regexp = "^HAR-\\d{6,12}$", message = "targetAccountNumber must look like HAR-000123")
        String targetAccountNumber,

        @Positive(message = "amountMinor must be a positive amount in minor units")
        long amountMinor,

        @Size(max = 140, message = "narrative must be at most 140 characters")
        String narrative) {
}
