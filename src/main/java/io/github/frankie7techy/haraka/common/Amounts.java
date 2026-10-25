package io.github.frankie7techy.haraka.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amounts are stored as integer minor units (cents) indexed by currency scale 2.
 * Example: KES 12,345.67 is stored as 1_234_567 minor units.
 *
 * <p>Money is never held as floating point anywhere in this service; conversion to a
 * display string happens only at the API boundary.</p>
 */
public final class Amounts {

    private Amounts() {
    }

    /** Formats minor units as a human-readable major string, e.g. 1234567 -> "12345.67". */
    public static String formatMinor(long amountMinor) {
        return BigDecimal.valueOf(amountMinor)
                .movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }
}
