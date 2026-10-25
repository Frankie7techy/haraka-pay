package io.github.frankie7techy.haraka.account;

/** Formats stable, human-readable account numbers used as the public account key. */
public final class AccountNumbers {

    private AccountNumbers() {
    }

    public static String ofUserId(long userId) {
        return "HAR-%06d".formatted(userId);
    }
}
