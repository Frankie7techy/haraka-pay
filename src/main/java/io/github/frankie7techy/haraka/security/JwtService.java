package io.github.frankie7techy.haraka.security;

import io.github.frankie7techy.haraka.config.AppProperties;
import io.github.frankie7techy.haraka.config.AppProperties.Jwt;
import io.github.frankie7techy.haraka.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless bearer tokens (HS256). The signing key comes from
 * configuration; a runtime fail-fast check refuses to boot with a weak secret.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // 256-bit floor for HS256

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties properties) {
        Jwt jwt = properties.jwt();
        byte[] secret = jwt.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "HARAKA_JWT_SECRET must be at least %d bytes; configure a strong value in production"
                            .formatted(MIN_SECRET_BYTES));
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = Duration.ofMinutes(jwt.ttlMinutes());
    }

    public String issueToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim("email", principal.email())
                .claim("role", principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and verifies a token. Returns the principal for valid tokens and null for
     * anything unusable, so callers can treat bad credentials exactly like no credentials.
     */
    public UserPrincipal verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new UserPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class),
                    AppUser.Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long ttlMinutes() {
        return ttl.toMinutes();
    }
}
