package io.github.frankie7techy.haraka.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A customer wallet.
 *
 * <p>Balances are mutable state, so every mutation happens under a database row lock
 * (see {@code WalletAccountRepository#findAllForUpdate}) plus an optimistic-locking
 * {@code version} column as a second line of defence. The optimistic column converts any
 * lock-ordering mistake into a failed flush instead of a silent lost update.</p>
 */
@Entity
@Table(name = "wallet_account")
public class WalletAccount {

    public enum Status {
        ACTIVE,
        FROZEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 16)
    private String accountNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletAccount() {
        // JPA
    }

    /**
     * The user id is the identity key, so the readable number is derived deterministically:
     * user 7 owns "HAR-000007". One user currently holds exactly one wallet, which keeps
     * the number unique without a second sequence round-trip before insert.
     */
    public WalletAccount(Long userId, String currency) {
        this.userId = userId;
        this.currency = currency;
        this.accountNumber = AccountNumbers.ofUserId(userId);
        this.balanceMinor = 0L;
        this.status = Status.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Reduces the balance; the caller has already checked funds and status under lock. */
    public void debit(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (balanceMinor < amountMinor) {
            throw new IllegalStateException("Balance too low for debit of " + amountMinor);
        }
        balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        balanceMinor += amountMinor;
    }

    /** Operations desk action: temporarily stops incoming/outgoing funds. */
    public void freeze() {
        this.status = Status.FROZEN;
    }

    public void unfreeze() {
        this.status = Status.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public Status getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
