package io.github.frankie7techy.haraka.common;

import io.github.frankie7techy.haraka.common.exception.ApiException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Bounded retry for optimistic-lock failures on wallet mutations.
 *
 * <p>Transfers serialize on {@code SELECT ... FOR UPDATE} row locks, and the
 * {@code version} column is the second line of defence. Some engines (notably H2 in
 * PostgreSQL mode, our test/CI database) hand a snapshot row back to a waiting
 * {@code FOR UPDATE} query, so a contender can lose the version check even though the
 * lock order was correct. The portable answer is the one production payments systems use
 * anyway: treat the version rejection as "state moved under you, re-read and retry",
 * with a small capped number of attempts and exponential backoff so heavy contention
 * degrades into a clean 503 instead of a lost update.</p>
 */
@Component
public class OptimisticRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(OptimisticRetryExecutor.class);
    private static final int MAX_ATTEMPTS = 8;
    private static final long BASE_BACKOFF_MILLIS = 25L;

    public <T> T execute(String operationName, Supplier<T> operation) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (OptimisticLockingFailureException contention) {
                lastFailure = contention;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleepWithBackoff(attempt);
                log.info("Optimistic lock contention on {} (attempt {}/{})", operationName, attempt, MAX_ATTEMPTS);
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTENTION",
                operationName + " could not complete under heavy contention; please retry");
    }

    private void sleepWithBackoff(int attempt) {
        try {
            long backoff = BASE_BACKOFF_MILLIS * (1L << Math.min(attempt, 5));
            Thread.sleep(backoff);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off optimistic-lock retry", interrupted);
        }
    }
}
