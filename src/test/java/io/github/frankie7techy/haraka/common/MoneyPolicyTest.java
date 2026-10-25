package io.github.frankie7techy.haraka.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.frankie7techy.haraka.common.exception.InvalidAmountException;
import io.github.frankie7techy.haraka.config.AppProperties;
import org.junit.jupiter.api.Test;

class MoneyPolicyTest {

    private final MoneyPolicy policy = new MoneyPolicy(new AppProperties(
            new AppProperties.Jwt("unused-signing-secret-0123456789abcdef", 60),
            new AppProperties.Transfer(1, 50_000_000L),
            new AppProperties.Topup(20_000_000L),
            new AppProperties.Idempotency(7)));

    @Test
    void acceptsAmountsInsideTheTransferWindow() {
        assertThatCode(() -> policy.validateTransferAmount(1L)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateTransferAmount(50_000_000L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroNegativeAndOverLimitTransferAmounts() {
        assertThatThrownBy(() -> policy.validateTransferAmount(0L)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> policy.validateTransferAmount(-100L)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> policy.validateTransferAmount(50_000_001L))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("500000.00");
    }

    @Test
    void appliesSeparateCeilingToTopups() {
        assertThatCode(() -> policy.validateTopupAmount(20_000_000L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateTopupAmount(20_000_001L)).isInstanceOf(InvalidAmountException.class);
        // the ceilings are independent: 30,000.00 KES is a legal transfer but an illegal topup
        assertThatCode(() -> policy.validateTransferAmount(30_000_000L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateTopupAmount(30_000_000L)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void exposesTheOperatingCurrency() {
        assertThat(policy.currency()).isEqualTo("KES");
    }
}
