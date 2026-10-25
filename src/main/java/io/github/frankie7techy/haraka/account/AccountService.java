package io.github.frankie7techy.haraka.account;

import io.github.frankie7techy.haraka.common.MoneyPolicy;
import io.github.frankie7techy.haraka.common.OptimisticRetryExecutor;
import io.github.frankie7techy.haraka.common.exception.ResourceNotFoundException;
import io.github.frankie7techy.haraka.ledger.LedgerEntryRepository;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Read model plus deposits for wallets. Statement paging is bounded (max 100 rows) so a
 * client cannot force unbounded result sets. Deposits wrap the transactional executor in
 * the same optimistic-lock retry used by transfers.
 */
@Service
public class AccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final MoneyPolicy moneyPolicy;
    private final TopupExecutor topupExecutor;
    private final OptimisticRetryExecutor optimisticRetry;

    public AccountService(WalletAccountRepository accounts, LedgerEntryRepository ledger,
                          MoneyPolicy moneyPolicy, TopupExecutor topupExecutor,
                          OptimisticRetryExecutor optimisticRetry) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.moneyPolicy = moneyPolicy;
        this.topupExecutor = topupExecutor;
        this.optimisticRetry = optimisticRetry;
    }

    public AccountView currentAccount(UserPrincipal caller) {
        return AccountView.from(ownedBy(caller));
    }

    public StatementPage statement(UserPrincipal caller, int page, int size) {
        WalletAccount account = ownedBy(caller);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<LedgerEntryView> mapped = ledger.findByAccountId(account.getId(), pageRequest)
                .map(entry -> LedgerEntryView.of(entry, account.getAccountNumber()));
        return new StatementPage(mapped.getNumber(), mapped.getSize(), mapped.getTotalElements(),
                mapped.getTotalPages(), mapped.getContent());
    }

    public LedgerEntryView topup(UserPrincipal caller, TopupRequest request) {
        moneyPolicy.validateTopupAmount(request.amountMinor());
        return optimisticRetry.execute("topup", () -> topupExecutor.topup(caller.userId(), request));
    }

    private WalletAccount ownedBy(UserPrincipal caller) {
        return accounts.findFirstByUserId(caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No wallet exists for the authenticated user"));
    }
}
