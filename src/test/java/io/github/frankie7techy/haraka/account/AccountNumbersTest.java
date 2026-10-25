package io.github.frankie7techy.haraka.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountNumbersTest {

    @Test
    void zeroPadsUserIdsToSixDigits() {
        assertThat(AccountNumbers.ofUserId(1L)).isEqualTo("HAR-000001");
        assertThat(AccountNumbers.ofUserId(424242L)).isEqualTo("HAR-424242");
        assertThat(AccountNumbers.ofUserId(4_242_424L)).isEqualTo("HAR-4242424");
    }

    @Test
    void distinctUsersNeverCollide() {
        assertThat(AccountNumbers.ofUserId(7L)).isNotEqualTo(AccountNumbers.ofUserId(8L));
    }
}
