package io.github.frankie7techy.haraka.idempotency;

import io.github.frankie7techy.haraka.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention job: finished responses are only useful for retries, so records older than
 * the configured retention window (default 7 days, including crashed IN_FLIGHT markers)
 * are purged to keep the unique-key index small.
 */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRecordRepository repository;
    private final AppProperties properties;

    public IdempotencyCleanupJob(IdempotencyRecordRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.idempotency().retentionDays()));
        int removed = repository.deleteCreatedBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} idempotency records older than {}", removed, cutoff);
        }
    }
}
