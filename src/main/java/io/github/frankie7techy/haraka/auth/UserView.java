package io.github.frankie7techy.haraka.auth;

import io.github.frankie7techy.haraka.user.AppUser;

/** Value view of the authenticated user, reused by auth payloads. */
public record UserView(long id, String email, String fullName, String role) {

    public static UserView from(AppUser user) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
