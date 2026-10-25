package io.github.frankie7techy.haraka.security;

import io.github.frankie7techy.haraka.user.AppUser;

/**
 * The authenticated caller attached to the security context by the JWT filter.
 * Records give value semantics, which makes service code cheap to read in tests.
 */
public record UserPrincipal(long userId, String email, AppUser.Role role) {

    public String authority() {
        return "ROLE_" + role.name();
    }
}
