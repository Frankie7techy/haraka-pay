package io.github.frankie7techy.haraka.transfer;

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
 * The instruction record for a P2P transfer. Failed attempts are persisted too (with a
 * failure reason) so operations can audit rejected instructions rather than only happy paths.
 */
@Entity
@Table(name = "transfer")
public class Transfer {

    public enum Status {
        COMPLETED,
        FAILED
    }

    public static final String REASON_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private Long targetAccountId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "failure_reason", length = 60)
    private String failureReason;

    @Column(name = "narrative", length = 140)
    private String narrative;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
        // JPA
    }

    public static Transfer pending(Long sourceAccountId, Long targetAccountId, long amountMinor, String narrative) {
        Transfer t = new Transfer();
        t.sourceAccountId = sourceAccountId;
        t.targetAccountId = targetAccountId;
        t.amountMinor = amountMinor;
        t.narrative = narrative;
        t.status = Status.FAILED; // written for audit; upgraded to COMPLETED on success
        return t;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getNarrative() {
        return narrative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason;
    }
}
