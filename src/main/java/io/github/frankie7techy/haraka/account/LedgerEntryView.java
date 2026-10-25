package io.github.frankie7techy.haraka.account;

import io.github.frankie7techy.haraka.common.Amounts;
import io.github.frankie7techy.haraka.ledger.LedgerEntry;
import java.time.Instant;

/**
 * One immutable statement line. Amounts carry both the integer minor value used by the
 * ledger and a formatted string so clients never re-implement money formatting.
 */
public record LedgerEntryView(
        long id,
        Long transferId,
        String accountNumber,
        String direction,
        long amountMinor,
        String amount,
        long balanceAfterMinor,
        String balanceAfter,
        String narrative,
        Instant createdAt) {

    public static LedgerEntryView of(LedgerEntry entry, String accountNumber) {
        return new LedgerEntryView(
                entry.getId(),
                entry.getTransferId(),
                accountNumber,
                entry.getDirection().name(),
                entry.getAmountMinor(),
                Amounts.formatMinor(entry.getAmountMinor()),
                entry.getBalanceAfterMinor(),
                Amounts.formatMinor(entry.getBalanceAfterMinor()),
                entry.getNarrative(),
                entry.getCreatedAt());
    }
}
