package io.github.frankie7techy.haraka.monitoring;

import io.github.frankie7techy.haraka.account.WalletAccount;
import io.github.frankie7techy.haraka.account.WalletAccountRepository;
import io.github.frankie7techy.haraka.ledger.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reconciliation health check. The core accounting invariant is: for every wallet,
 * mutable balance == sum of its ledger entries. A breach means the ledger and the
 * balances have diverged, which is the loudest possible alarm in a payments system, so
 * it is surfaced as DOWN and fails the whole health endpoint.
 */
@Component("ledgerIntegrity")
public class LedgerIntegrityHealthIndicator implements HealthIndicator {

    private final WalletAccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public LedgerIntegrityHealthIndicator(WalletAccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Override
    public Health health() {
        Map<Long, Long> netPerAccount = new HashMap<>();
        for (Object[] row : ledger.netMinorPerAccount()) {
            netPerAccount.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<WalletAccount> all = accounts.findAll();
        List<String> mismatches = new ArrayList<>();
        for (WalletAccount account : all) {
            long ledgerNet = netPerAccount.getOrDefault(account.getId(), 0L);
            if (ledgerNet != account.getBalanceMinor()) {
                mismatches.add("%s: balance=%d ledgerNet=%d".formatted(
                        account.getAccountNumber(), account.getBalanceMinor(), ledgerNet));
            }
        }

        if (mismatches.isEmpty()) {
            return Health.up().withDetail("accountsChecked", all.size()).build();
        }
        return Health.down()
                .withDetail("accountsChecked", all.size())
                .withDetail("mismatchedAccounts", mismatches)
                .build();
    }
}
