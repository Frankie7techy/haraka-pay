package io.github.frankie7techy.haraka.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The immutable money trail. Every balance change is expressed as exactly one entry per
 * touched account (DEBIT against the source, CREDIT against the target), which makes
 * balances independently replayable for reconciliation.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    public enum Direction {
        DEBIT,
        CREDIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    /** Balance snapshot after this entry posted, for auditability without replaying. */
    @Column(name = "balance_after_minor", nullable = false)
    private long balanceAfterMinor;

    @Column(name = "narrative", length = 140)
    private String narrative;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    public LedgerEntry(Long transferId, Long accountId, Direction direction, long amountMinor,
                       long balanceAfterMinor, String narrative) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.balanceAfterMinor = balanceAfterMinor;
        this.narrative = narrative;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getBalanceAfterMinor() {
        return balanceAfterMinor;
    }

    public String getNarrative() {
        return narrative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
