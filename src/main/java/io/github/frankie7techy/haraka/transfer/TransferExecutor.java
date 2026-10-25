package io.github.frankie7techy.haraka.transfer;

import io.github.frankie7techy.haraka.account.WalletAccount;
import io.github.frankie7techy.haraka.account.WalletAccountRepository;
import io.github.frankie7techy.haraka.common.Amounts;
import io.github.frankie7techy.haraka.common.exception.AccountUnavailableException;
import io.github.frankie7techy.haraka.common.exception.InsufficientFundsException;
import io.github.frankie7techy.haraka.common.exception.InvalidTransferException;
import io.github.frankie7techy.haraka.common.exception.ResourceNotFoundException;
import io.github.frankie7techy.haraka.ledger.LedgerEntry;
import io.github.frankie7techy.haraka.ledger.LedgerEntryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The transactional heart of a P2P transfer: lock both wallets in a deterministic order,
 * move the balances, and write the transfer record plus its two ledger legs atomically.
 * Retry-on-contention is deliberately NOT handled here (see {@code OptimisticRetryExecutor});
 * one invocation is exactly one transactional attempt.
 */
@Component
public class TransferExecutor {

    private final WalletAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;
    private final TransferAudit audit;

    public TransferExecutor(WalletAccountRepository accounts, LedgerEntryRepository ledger,
                            TransferRepository transfers, TransferAudit audit) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.audit = audit;
    }

    @Transactional
    public TransferResponse transfer(long callerUserId, NewTransferRequest request) {
        long amountMinor = request.amountMinor();

        WalletAccount source = accounts.findFirstByUserId(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No wallet exists for the authenticated user"));
        WalletAccount target = accounts.findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target account %s does not exist".formatted(request.targetAccountNumber())));

        if (source.getId().equals(target.getId())) {
            throw new InvalidTransferException("A wallet cannot transfer to itself");
        }

        // Deterministic lock order: always ascending account id, whichever direction the
        // money flows. This is what structurally prevents the A->B / B->A deadlock.
        List<WalletAccount> locked = accounts.findAllForUpdate(List.of(source.getId(), target.getId()));
        Map<Long, WalletAccount> byId = locked.stream()
                .collect(Collectors.toMap(WalletAccount::getId, Function.identity()));
        WalletAccount lockedSource = byId.get(source.getId());
        WalletAccount lockedTarget = byId.get(target.getId());

        requireActive(lockedSource, "Source");
        requireActive(lockedTarget, "Target");
        if (!lockedSource.getCurrency().equals(lockedTarget.getCurrency())) {
            throw new InvalidTransferException("Cross-currency transfers are not supported");
        }

        if (lockedSource.getBalanceMinor() < amountMinor) {
            Transfer failed = Transfer.pending(lockedSource.getId(), lockedTarget.getId(), amountMinor,
                    request.narrative());
            failed.markFailed(Transfer.REASON_INSUFFICIENT_FUNDS);
            audit.recordFailed(failed); // independent transaction: the audit row survives this rollback
            throw new InsufficientFundsException(
                    "Insufficient funds: available %s %s, requested %s %s".formatted(
                            Amounts.formatMinor(lockedSource.getBalanceMinor()), lockedSource.getCurrency(),
                            Amounts.formatMinor(amountMinor), lockedSource.getCurrency()));
        }

        lockedSource.debit(amountMinor);
        lockedTarget.credit(amountMinor);

        Transfer transfer = Transfer.pending(lockedSource.getId(), lockedTarget.getId(), amountMinor,
                request.narrative());
        transfer.markCompleted();
        Transfer saved = transfers.save(transfer);

        ledger.save(new LedgerEntry(saved.getId(), lockedSource.getId(), LedgerEntry.Direction.DEBIT,
                amountMinor, lockedSource.getBalanceMinor(), request.narrative()));
        ledger.save(new LedgerEntry(saved.getId(), lockedTarget.getId(), LedgerEntry.Direction.CREDIT,
                amountMinor, lockedTarget.getBalanceMinor(), request.narrative()));

        return TransferResponse.of(saved, lockedSource.getAccountNumber(), lockedTarget.getAccountNumber());
    }

    private void requireActive(WalletAccount account, String side) {
        if (account == null) {
            throw new ResourceNotFoundException("Account locked row missing during transfer");
        }
        if (account.getStatus() != WalletAccount.Status.ACTIVE) {
            throw new AccountUnavailableException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_UNAVAILABLE",
                    "%s wallet %s is not active".formatted(side, account.getAccountNumber()));
        }
    }
}
