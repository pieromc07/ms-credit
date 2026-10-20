package com.nttdata.mscredit.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.exception.BusinessException;

class AmountsTest {

    @Test
    void acceptsPositiveAmount() {
        assertThatCode(() -> Amounts.requirePositive(new BigDecimal("0.01"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullAmount() {
        assertRejected(null);
    }

    @Test
    void rejectsZeroAmount() {
        assertRejected(BigDecimal.ZERO);
    }

    @Test
    void rejectsNegativeAmount() {
        assertRejected(new BigDecimal("-5.00"));
    }

    private static void assertRejected(BigDecimal amount) {
        assertThatThrownBy(() -> Amounts.requirePositive(amount))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }
}
