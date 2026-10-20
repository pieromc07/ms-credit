package com.nttdata.mscredit.model;

import static com.nttdata.mscredit.support.Fixtures.card;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreditCardTest {

    @Test
    void availableCreditIsLimitMinusConsumedAmount() {
        assertThat(card("c1", "5000.00", "1200.00").availableCredit()).isEqualByComparingTo("3800.00");
    }

    @Test
    void fullyConsumedCardHasNoAvailableCredit() {
        assertThat(card("c1", "5000.00", "5000.00").availableCredit()).isEqualByComparingTo("0");
    }
}
