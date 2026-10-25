package io.github.frankie7techy.haraka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed view of the {@code haraka.*} configuration tree. Records are used so
 * properties are immutable; profiles (local/test/CI) differ only by key, not by class.
 */
@ConfigurationProperties(prefix = "haraka")
public record AppProperties(Jwt jwt, Transfer transfer, Topup topup, Idempotency idempotency) {

    public record Jwt(String secret, int ttlMinutes) {
    }

    public record Transfer(long minAmountMinor, long maxAmountMinor) {
    }

    public record Topup(long maxAmountMinor) {
    }

    public record Idempotency(int retentionDays) {
    }
}
