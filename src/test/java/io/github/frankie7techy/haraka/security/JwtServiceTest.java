package io.github.frankie7techy.haraka.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.frankie7techy.haraka.config.AppProperties;
import io.github.frankie7techy.haraka.user.AppUser;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String STRONG_SECRET = "unit-test-secret-key-0123456789-never-production";

    private JwtService serviceWithSecret(String secret) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(secret, 30),
                new AppProperties.Transfer(1, 50_000_000L),
                new AppProperties.Topup(20_000_000L),
                new AppProperties.Idempotency(7));
        return new JwtService(properties);
    }

    @Test
    void issuesAndVerifiesATokenRoundTrip() {
        JwtService service = serviceWithSecret(STRONG_SECRET);
        UserPrincipal original = new UserPrincipal(42L, "frankie@haraka.test", AppUser.Role.USER);

        String token = service.issueToken(original);
        UserPrincipal parsed = service.verify(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("frankie@haraka.test");
        assertThat(parsed.role()).isEqualTo(AppUser.Role.USER);
        assertThat(parsed.authority()).isEqualTo("ROLE_USER");
        assertThat(service.ttlMinutes()).isEqualTo(30);
    }

    @Test
    void rejectsTamperedTokensAsNull() {
        JwtService service = serviceWithSecret(STRONG_SECRET);
        String token = service.issueToken(new UserPrincipal(1L, "a@b.test", AppUser.Role.USER));
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".bogussignature";

        assertThat(service.verify(tampered)).isNull();
        assertThat(service.verify("not-a-jwt")).isNull();
        assertThat(service.verify("")).isNull();
    }

    @Test
    void refusesToBootWithAWeakSigningSecret() {
        assertThatThrownBy(() -> serviceWithSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARAKA_JWT_SECRET");
    }
}
