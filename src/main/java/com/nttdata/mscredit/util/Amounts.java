package com.nttdata.mscredit.util;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.exception.BusinessException;

public final class Amounts {

    private Amounts() {
    }

    public static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
    }
}
