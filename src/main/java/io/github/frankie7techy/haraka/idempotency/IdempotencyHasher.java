package io.github.frankie7techy.haraka.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Canonicalised request fingerprint. The hash covers the endpoint in addition to the JSON
 * body, so a key cannot be replayed across different operations.
 */
@Component
public class IdempotencyHasher {

    public static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    public String hash(String endpoint, String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(endpoint.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0x1f);
            digest.update(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Keys must be client-meaningful, bounded and URL-safe: 16..64 chars from
     * [A-Za-z0-9_-]. The 16-char floor stops callers from inflating a whole index
     * with trivially identical ids.
     */
    public boolean isKeyValid(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
}
