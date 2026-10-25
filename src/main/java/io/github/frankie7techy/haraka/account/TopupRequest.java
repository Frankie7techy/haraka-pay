package io.github.frankie7techy.haraka.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** A simulated M-Pesa-style deposit. Minor units: KES 1.00 == 100. */
public record TopupRequest(
        @Positive(message = "amountMinor must be a positive amount in minor units")
        @Max(value = Long.MAX_VALUE, message = "amountMinor overflow")
        long amountMinor,
        String narrative) {
}
