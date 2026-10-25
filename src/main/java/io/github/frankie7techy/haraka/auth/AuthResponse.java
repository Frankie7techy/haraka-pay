package io.github.frankie7techy.haraka.auth;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInMinutes,
        UserView user,
        io.github.frankie7techy.haraka.account.AccountView account) {
}
