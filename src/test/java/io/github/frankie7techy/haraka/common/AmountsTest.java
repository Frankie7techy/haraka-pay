package io.github.frankie7techy.haraka.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmountsTest {

    @Test
    void formatsMinorUnitsAsMajorString() {
        assertThat(Amounts.formatMinor(0L)).isEqualTo("0.00");
        assertThat(Amounts.formatMinor(1L)).isEqualTo("0.01");
        assertThat(Amounts.formatMinor(100L)).isEqualTo("1.00");
        assertThat(Amounts.formatMinor(1_234_567L)).isEqualTo("12345.67");
        assertThat(Amounts.formatMinor(-250L)).isEqualTo("-2.50");
    }
}
