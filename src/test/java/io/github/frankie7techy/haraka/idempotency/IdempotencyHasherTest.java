package io.github.frankie7techy.haraka.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyHasherTest {

    private final IdempotencyHasher hasher = new IdempotencyHasher();

    @Test
    void acceptsBoundedUrlSafeKeys() {
        assertThat(hasher.isKeyValid("abc-123_ABC-def456")).isTrue();
        assertThat(hasher.isKeyValid("a".repeat(16))).isTrue();
        assertThat(hasher.isKeyValid("a".repeat(64))).isTrue();
    }

    @Test
    void rejectsKeysThatWouldPoisonTheIndex() {
        assertThat(hasher.isKeyValid(null)).isFalse();
        assertThat(hasher.isKeyValid("short-key")).isFalse();                       // < 16 chars
        assertThat(hasher.isKeyValid("a".repeat(65))).isFalse();                    // > 64 chars
        assertThat(hasher.isKeyValid("has spaces")).isFalse();
        assertThat(hasher.isKeyValid("injection/path")).isFalse();
    }

    @Test
    void sameEndpointAndBodyProduceSameHash() {
        String first = hasher.hash("POST /api/v1/transfers", "{\"amountMinor\":1000}");
        String second = hasher.hash("POST /api/v1/transfers", "{\"amountMinor\":1000}");
        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentPayloadOrEndpointChangesTheHash() {
        String base = hasher.hash("POST /api/v1/transfers", "{\"amountMinor\":1000}");
        assertThat(hasher.hash("POST /api/v1/transfers", "{\"amountMinor\":2000}")).isNotEqualTo(base);
        assertThat(hasher.hash("POST /api/v1/accounts/me/topups", "{\"amountMinor\":1000}")).isNotEqualTo(base);
    }
}
