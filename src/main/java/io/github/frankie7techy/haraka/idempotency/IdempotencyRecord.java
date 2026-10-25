package io.github.frankie7techy.haraka.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Records every money-mutation request handled under a caller-supplied idempotency key.
 * A {@link #statusCode} of {@link #IN_FLIGHT} marks an operation that started but has not
 * produced a response yet, which lets the API reject duplicate replays of an in-progress
 * transaction instead of silently executing it twice.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    public static final int IN_FLIGHT = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "endpoint", nullable = false, length = 64)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(Long userId, String idempotencyKey, String endpoint, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.statusCode = IN_FLIGHT;
        this.responseBody = "";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isInFlight() {
        return statusCode == IN_FLIGHT;
    }

    public boolean hashMatches(String candidateHash) {
        return requestHash.equals(candidateHash);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void complete(int statusCode, String responseBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
