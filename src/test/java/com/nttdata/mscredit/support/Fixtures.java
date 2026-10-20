package com.nttdata.mscredit.support;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.nttdata.mscredit.config.CardLimitProperties;
import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.CreditCardStatus;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-05-10T10:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 5, 10);
    public static final CardLimitProperties LIMITS =
            new CardLimitProperties(new BigDecimal("5000.00"), new BigDecimal("20000.00"));

    private Fixtures() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static CreditProduct product(String id, String outstanding) {
        return product(id, CustomerType.PERSONAL, outstanding, TODAY.plusMonths(1), CreditProductStatus.ACTIVE);
    }

    public static CreditProduct product(String id, CustomerType type, String outstanding,
            LocalDate dueDate, CreditProductStatus status) {
        return CreditProduct.builder()
                .id(id)
                .customerId("cust-1")
                .customerType(type)
                .amount(new BigDecimal("1000.00"))
                .outstandingBalance(new BigDecimal(outstanding))
                .dueDate(dueDate)
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    public static CreditCard card(String id, String limit, String consumed) {
        return card(id, CustomerType.PERSONAL, limit, consumed, CreditCardStatus.ACTIVE);
    }

    public static CreditCard card(String id, CustomerType type, String limit, String consumed,
            CreditCardStatus status) {
        return CreditCard.builder()
                .id(id)
                .customerId("cust-1")
                .customerType(type)
                .maskedNumber("**** **** **** 1234")
                .creditLimit(new BigDecimal(limit))
                .consumedAmount(new BigDecimal(consumed))
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
