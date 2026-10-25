package io.github.frankie7techy.haraka.common;

import io.github.frankie7techy.haraka.common.exception.InvalidAmountException;
import io.github.frankie7techy.haraka.config.AppProperties;
import org.springframework.stereotype.Component;

/** Enforces the business rules that apply to every monetary operation. */
@Component
public class MoneyPolicy {

    public static final String CURRENCY = "KES";

    private final AppProperties properties;

    public MoneyPolicy(AppProperties properties) {
        this.properties = properties;
    }

    public String currency() {
        return CURRENCY;
    }

    /** Rejects non-positive, below-limit and above-ceiling amounts. */
    public void validateTransferAmount(long amountMinor) {
        validatePositive(amountMinor, "Transfer amount");
        if (amountMinor > properties.transfer().maxAmountMinor()) {
            throw new InvalidAmountException(
                    "Transfer amount exceeds the per-transfer limit of %s %s"
                            .formatted(Amounts.formatMinor(properties.transfer().maxAmountMinor()), CURRENCY));
        }
    }

    public void validateTopupAmount(long amountMinor) {
        validatePositive(amountMinor, "Topup amount");
        if (amountMinor > properties.topup().maxAmountMinor()) {
            throw new InvalidAmountException(
                    "Topup amount exceeds the per-operation ceiling of %s %s"
                            .formatted(Amounts.formatMinor(properties.topup().maxAmountMinor()), CURRENCY));
        }
    }

    private void validatePositive(long amountMinor, String label) {
        if (amountMinor < properties.transfer().minAmountMinor()) {
            throw new InvalidAmountException(label + " must be at least %s %s"
                    .formatted(Amounts.formatMinor(properties.transfer().minAmountMinor()), CURRENCY));
        }
    }
}
