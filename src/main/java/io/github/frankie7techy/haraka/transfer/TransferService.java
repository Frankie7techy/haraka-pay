package io.github.frankie7techy.haraka.transfer;

import io.github.frankie7techy.haraka.account.LedgerEntryView;
import io.github.frankie7techy.haraka.account.WalletAccount;
import io.github.frankie7techy.haraka.account.WalletAccountRepository;
import io.github.frankie7techy.haraka.common.MoneyPolicy;
import io.github.frankie7techy.haraka.common.OptimisticRetryExecutor;
import io.github.frankie7techy.haraka.common.exception.ResourceNotFoundException;
import io.github.frankie7techy.haraka.ledger.LedgerEntryRepository;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Transfer orchestration: policy validation once, then bounded optimistic-lock retries
 * around the transactional executor. Reads (get/entries) enforce participant visibility
 * with 404s so the API never confirms other customers' transfers exist.
 */
@Service
public class TransferService {

    private final TransferExecutor executor;
    private final OptimisticRetryExecutor optimisticRetry;
    private final MoneyPolicy moneyPolicy;
    private final WalletAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;

    public TransferService(TransferExecutor executor, OptimisticRetryExecutor optimisticRetry,
                           MoneyPolicy moneyPolicy, WalletAccountRepository accounts,
                           LedgerEntryRepository ledger, TransferRepository transfers) {
        this.executor = executor;
        this.optimisticRetry = optimisticRetry;
        this.moneyPolicy = moneyPolicy;
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
    }

    public TransferResponse transfer(UserPrincipal caller, NewTransferRequest request) {
        moneyPolicy.validateTransferAmount(request.amountMinor());
        return optimisticRetry.execute("transfer", () -> executor.transfer(caller.userId(), request));
    }

    public TransferResponse get(UserPrincipal caller, long transferId) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer %d not found".formatted(transferId)));
        List<WalletAccount> sides = sidesOf(transfer);
        requireParticipant(caller, sides);
        return TransferResponse.of(transfer, sides.get(0).getAccountNumber(), sides.get(1).getAccountNumber());
    }

    public List<LedgerEntryView> entries(UserPrincipal caller, long transferId) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer %d not found".formatted(transferId)));
        List<WalletAccount> sides = sidesOf(transfer);
        requireParticipant(caller, sides);
        Map<Long, String> numberById = sides.stream()
                .collect(Collectors.toMap(WalletAccount::getId, WalletAccount::getAccountNumber));
        return ledger.findByTransferIdOrderByIdAsc(transferId).stream()
                .map(entry -> LedgerEntryView.of(entry, numberById.get(entry.getAccountId())))
                .toList();
    }

    private List<WalletAccount> sidesOf(Transfer transfer) {
        List<Long> ids = new ArrayList<>(List.of(transfer.getSourceAccountId(), transfer.getTargetAccountId()));
        List<WalletAccount> found = accounts.findAllById(ids);
        if (found.size() != 2) {
            throw new ResourceNotFoundException("Transfer %d references missing wallets".formatted(transfer.getId()));
        }
        return found;
    }

    private void requireParticipant(UserPrincipal caller, List<WalletAccount> sides) {
        boolean participant = sides.stream().anyMatch(account -> caller.userId() == account.getUserId());
        if (!participant) {
            // 404 rather than 403: never confirm to a caller that someone else's transfer exists
            throw new ResourceNotFoundException("Transfer not found");
        }
    }
}
