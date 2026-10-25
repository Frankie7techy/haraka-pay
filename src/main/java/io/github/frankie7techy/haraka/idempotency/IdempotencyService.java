package io.github.frankie7techy.haraka.idempotency;

import io.github.frankie7techy.haraka.common.exception.IdempotencyException;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a money-mutating operation at most once per caller-supplied key and replays
 * the exact stored response for honest retries.
 *
 * <p>Semantics matrix for (user, key):</p>
 * <ul>
 *   <li>no record                -> run the operation, persist (status, body)</li>
 *   <li>record, different endpoint or hash -> 409 conflict (key reuse / client bug)</li>
 *   <li>record, finished        -> replay stored response verbatim, no re-execution</li>
 *   <li>record, IN_FLIGHT       -> 409 "already in progress" (concurrent duplicate)</li>
 *   <li>insert race on record   -> loser adopts or replays the winner atomically</li>
 * </ul>
 *
 * <p>The IN_FLIGHT marker is committed in its own transaction so a concurrent duplicate
 * is rejected immediately; it is deleted if the operation fails, so genuine failures stay
 * retryable. If the process dies mid-operation the marker survives until retention
 * cleanup — documented in {@code docs/DESIGN_DECISIONS.md}.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /** Either a fresh execution or a stored replay waiting to be returned. */
    public sealed interface Reservation {
        record Start(IdempotencyRecord record) implements Reservation {
        }

        record Replay(int statusCode, String jsonBody) implements Reservation {
        }
    }

    public record StoredResponse(int statusCode, String jsonBody, boolean replay) {
    }

    /**
     * Reserves the key for this request. Independent per-reservation decision encoded in
     * the return type: callers either execute the operation or short-circuit with a replay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(Long userId, String key, String endpoint, String requestHash) {
        IdempotencyRecord existing = repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing == null) {
            try {
                return new Reservation.Start(repository.save(new IdempotencyRecord(userId, key, endpoint, requestHash)));
            } catch (DataIntegrityViolationException lostInsertRace) {
                // We lost the insert race but the winner's row is not committed/visible yet:
                // by definition their request is in progress, so reject as a concurrent duplicate.
                existing = repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
                if (existing == null) {
                    throw new IdempotencyException("A request with this Idempotency-Key is already in progress");
                }
            }
        }
        validateMatches(existing, endpoint, requestHash);
        if (existing.isInFlight()) {
            throw new IdempotencyException("A request with this Idempotency-Key is already in progress");
        }
        return new Reservation.Replay(existing.getStatusCode(), existing.getResponseBody());
    }

    /** Returns the exact stored answer for a request that already completed. */
    public StoredResponse replayed(Reservation.Replay replay) {
        return new StoredResponse(replay.statusCode(), replay.jsonBody(), true);
    }

    /** Persists the finished response so every future retry gets this exact answer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long recordId, int statusCode, String jsonBody) {
        repository.findById(recordId).ifPresent(record -> record.complete(statusCode, jsonBody));
        log.debug("Idempotency record {} completed with status {}", recordId, statusCode);
    }

    /** Frees the key after a failed operation so the client can retry cleanly. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long recordId) {
        repository.deleteById(recordId);
        log.info("Released idempotency key record {} after operation failure", recordId);
    }

    private void validateMatches(IdempotencyRecord record, String endpoint, String requestHash) {
        boolean endpointMatches = Objects.equals(record.getEndpoint(), endpoint);
        boolean hashMatches = Objects.equals(record.getRequestHash(), requestHash);
        if (!endpointMatches || !hashMatches) {
            throw new IdempotencyException(
                    "Idempotency-Key was already used for a different request (endpoint %s)"
                            .formatted(record.getEndpoint()));
        }
    }
}
