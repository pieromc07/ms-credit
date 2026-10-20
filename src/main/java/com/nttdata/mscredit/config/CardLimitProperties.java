package com.nttdata.mscredit.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.nttdata.mscredit.model.CustomerType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "credit.card.limit")
public record CardLimitProperties(
        @NotNull @Positive BigDecimal personal,
        @NotNull @Positive BigDecimal business) {

    public BigDecimal limitFor(CustomerType customerType) {
        return customerType == CustomerType.BUSINESS ? business : personal;
    }
}
