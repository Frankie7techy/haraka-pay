package io.github.frankie7techy.haraka.ledger;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    List<LedgerEntry> findByTransferIdOrderByIdAsc(Long transferId);

    /**
     * Net money movement per account, computed straight against the ledger rather than
     * trusting the mutable balance column. Powers the reconciliation health indicator.
     *
     * <p>Native SQL with a {@code CASE} expression keeps the arithmetic identical across
     * PostgreSQL and H2; the result rows are mapped manually in the service layer.</p>
     */
    @Query(value = """
            select account_id, sum(case when direction = 'DEBIT' then -amount_minor else amount_minor end)
            from ledger_entry
            group by account_id
            """, nativeQuery = true)
    List<Object[]> netMinorPerAccount();

    long countByTransferId(Long transferId);
}
