package com.nttdata.mscredit.config;

import static com.nttdata.mscredit.support.Fixtures.LIMITS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.nttdata.mscredit.model.CustomerType;

class CardLimitPropertiesTest {

    @Test
    void personalCustomersGetPersonalLimit() {
        assertThat(LIMITS.limitFor(CustomerType.PERSONAL)).isEqualByComparingTo("5000.00");
    }

    @Test
    void businessCustomersGetBusinessLimit() {
        assertThat(LIMITS.limitFor(CustomerType.BUSINESS)).isEqualByComparingTo("20000.00");
    }
}
