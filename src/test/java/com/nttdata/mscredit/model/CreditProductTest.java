package com.nttdata.mscredit.model;

import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreditProductTest {

    @Test
    void activeProductWithBalancePastDueIsOverdue() {
        CreditProduct product = product("p1", CustomerType.PERSONAL, "100.00", TODAY.minusDays(1),
                CreditProductStatus.ACTIVE);

        assertThat(product.isOverdue(TODAY)).isTrue();
    }

    @Test
    void productDueTodayIsNotOverdue() {
        CreditProduct product = product("p1", CustomerType.PERSONAL, "100.00", TODAY,
                CreditProductStatus.ACTIVE);

        assertThat(product.isOverdue(TODAY)).isFalse();
    }

    @Test
    void paidProductIsNeverOverdue() {
        CreditProduct product = product("p1", CustomerType.PERSONAL, "0.00", TODAY.minusDays(5),
                CreditProductStatus.PAID);

        assertThat(product.isOverdue(TODAY)).isFalse();
    }

    @Test
    void productWithoutOutstandingBalanceIsNotOverdue() {
        CreditProduct product = product("p1", CustomerType.PERSONAL, "0.00", TODAY.minusDays(5),
                CreditProductStatus.ACTIVE);

        assertThat(product.isOverdue(TODAY)).isFalse();
    }
}
