package io.github.frankie7techy.haraka.account;

import io.github.frankie7techy.haraka.common.Amounts;
import java.time.Instant;

/** Public view of a wallet; never exposes internal ids or version counters. */
public record AccountView(
        String accountNumber,
        String currency,
        long balanceMinor,
        String balance,
        String status,
        Instant createdAt) {

    public static AccountView from(WalletAccount account) {
        return new AccountView(
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalanceMinor(),
                Amounts.formatMinor(account.getBalanceMinor()),
                account.getStatus().name(),
                account.getCreatedAt());
    }
}
