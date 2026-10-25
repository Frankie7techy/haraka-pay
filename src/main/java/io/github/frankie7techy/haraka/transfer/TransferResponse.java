package io.github.frankie7techy.haraka.transfer;

import io.github.frankie7techy.haraka.common.Amounts;
import java.time.Instant;

public record TransferResponse(
        long id,
        String sourceAccountNumber,
        String targetAccountNumber,
        long amountMinor,
        String amount,
        String status,
        String failureReason,
        String narrative,
        Instant createdAt) {

    public static TransferResponse of(Transfer transfer, String sourceNumber, String targetNumber) {
        return new TransferResponse(
                transfer.getId(),
                sourceNumber,
                targetNumber,
                transfer.getAmountMinor(),
                Amounts.formatMinor(transfer.getAmountMinor()),
                transfer.getStatus().name(),
                transfer.getFailureReason(),
                transfer.getNarrative(),
                transfer.getCreatedAt());
    }
}
