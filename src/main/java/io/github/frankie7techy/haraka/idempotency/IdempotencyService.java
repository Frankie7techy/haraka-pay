package io.github.frankie7techy.haraka.idempotency;

import io.github.frankie7techy.haraka.common.exception.IdempotencyException;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes a money-mutating operation at most once per caller-supplied key and replays
 * the exact stored response for honest retries.
 *
 * <p>Semantics matrix for (user, key):</p>
 * <ul>
 *   <li>no record                -> run the operation, persist (status, body)</li>
 *   <li>record, different endpoint or hash -> 409 conflict (key reuse / client bug)</li>
 *   <li>record, finished         -> replay stored response verbatim, no re-execution</li>
 *   <li>record, IN_FLIGHT        -> 409 "already in progress" (concurrent duplicate)</li>
 *   <li>insert race on record    -> loser adopts or replays the winner atomically</li>
 * </ul>
 *
 * <p>The existence check, the insert and the race adoption each run in their own
 * {@code REQUIRES_NEW} transaction. That matters after a lost insert race: the failed
 * insert leaves the winning row uncommitted and the loser's persistence context poisoned
 * with a null-id entity, so the adoption read must happen in a fresh transaction to see
 * the winner once it commits.</p>
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
    private final TransactionTemplate requiresNewTx;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = template;
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
     * Reserves the key for this request. The outcome is encoded in the return type:
     * callers either execute the operation or short-circuit with a replay.
     */
    public Reservation reserve(Long userId, String key, String endpoint, String requestHash) {
        IdempotencyRecord existing = inNewTx(
                () -> repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null));
        if (existing != null) {
            return adopt(existing, endpoint, requestHash);
        }
        try {
            IdempotencyRecord created = inNewTx(
                    () -> repository.save(new IdempotencyRecord(userId, key, endpoint, requestHash)));
            return new Reservation.Start(created);
        } catch (DataIntegrityViolationException lostInsertRace) {
            // Lost the insert race. The winner's row may not be visible yet (it commits in
            // its own transaction), which by definition means their request is in progress.
            IdempotencyRecord winner = inNewTx(
                    () -> repository.findByUserIdAndIdempotencyKey(userId, key).orElse(null));
            if (winner == null) {
                throw new IdempotencyException("A request with this Idempotency-Key is already in progress");
            }
            return adopt(winner, endpoint, requestHash);
        }
    }

    private Reservation adopt(IdempotencyRecord record, String endpoint, String requestHash) {
        validateMatches(record, endpoint, requestHash);
        if (record.isInFlight()) {
            throw new IdempotencyException("A request with this Idempotency-Key is already in progress");
        }
        return new Reservation.Replay(record.getStatusCode(), record.getResponseBody());
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

    private <T> T inNewTx(Supplier<T> work) {
        return requiresNewTx.execute(status -> work.get());
    }
}
