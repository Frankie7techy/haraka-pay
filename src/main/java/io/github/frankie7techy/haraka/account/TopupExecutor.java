package io.github.frankie7techy.haraka.account;

import io.github.frankie7techy.haraka.common.MoneyPolicy;
import io.github.frankie7techy.haraka.common.exception.AccountUnavailableException;
import io.github.frankie7techy.haraka.common.exception.ResourceNotFoundException;
import io.github.frankie7techy.haraka.ledger.LedgerEntry;
import io.github.frankie7techy.haraka.ledger.LedgerEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The transactional deposit step; contention retry lives in the calling service. */
@Component
public class TopupExecutor {

    private static final String DEFAULT_TOPUP_NARRATIVE = "Simulated deposit";

    private final WalletAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final MoneyPolicy moneyPolicy;

    public TopupExecutor(WalletAccountRepository accounts, LedgerEntryRepository ledger, MoneyPolicy moneyPolicy) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.moneyPolicy = moneyPolicy;
    }

    @Transactional
    public LedgerEntryView topup(long callerUserId, TopupRequest request) {
        moneyPolicy.validateTopupAmount(request.amountMinor());
        WalletAccount account = accounts.findFirstByUserId(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No wallet exists for the authenticated user"));
        requireActive(account);
        account.credit(request.amountMinor());
        String narrative = request.narrative() == null || request.narrative().isBlank()
                ? DEFAULT_TOPUP_NARRATIVE
                : request.narrative();
        LedgerEntry entry = ledger.save(new LedgerEntry(null, account.getId(),
                LedgerEntry.Direction.CREDIT, request.amountMinor(), account.getBalanceMinor(), narrative));
        return LedgerEntryView.of(entry, account.getAccountNumber());
    }

    private void requireActive(WalletAccount account) {
        if (account.getStatus() != WalletAccount.Status.ACTIVE) {
            throw new AccountUnavailableException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_UNAVAILABLE",
                    "Wallet %s is not active".formatted(account.getAccountNumber()));
        }
    }
}
