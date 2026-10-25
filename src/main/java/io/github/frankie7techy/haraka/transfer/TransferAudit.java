package io.github.frankie7techy.haraka.transfer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists rejected transfer instructions in an independent transaction so the audit
 * record survives the rollback of the failed business transaction.
 *
 * <p>Locking note: this only inserts into {@code transfer}, never into
 * {@code wallet_account}, so it cannot deadlock against the row locks the caller holds
 * on the two accounts.</p>
 */
@Component
public class TransferAudit {

    private final TransferRepository transfers;

    public TransferAudit(TransferRepository transfers) {
        this.transfers = transfers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(Transfer failed) {
        transfers.save(failed);
    }
}
