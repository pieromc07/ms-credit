package com.nttdata.mscredit.mapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.CreditCardStatus;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CreditMapper {

    private final Clock clock;

    public com.nttdata.mscredit.generated.dto.CreditProduct toProductDto(CreditProduct product) {
        return new com.nttdata.mscredit.generated.dto.CreditProduct()
                .id(product.getId())
                .customerId(product.getCustomerId())
                .customerType(toApiCustomerType(product.getCustomerType()))
                .amount(toDouble(product.getAmount()))
                .outstandingBalance(toDouble(product.getOutstandingBalance()))
                .dueDate(product.getDueDate())
                .status(toApiProductStatus(product.getStatus()))
                .overdue(product.isOverdue(LocalDate.now(clock)))
                .createdAt(toOffsetDateTime(product.getCreatedAt()))
                .updatedAt(toOffsetDateTime(product.getUpdatedAt()));
    }

    public com.nttdata.mscredit.generated.dto.CreditCard toCardDto(CreditCard card) {
        return new com.nttdata.mscredit.generated.dto.CreditCard()
                .id(card.getId())
                .customerId(card.getCustomerId())
                .customerType(toApiCustomerType(card.getCustomerType()))
                .maskedNumber(card.getMaskedNumber())
                .creditLimit(toDouble(card.getCreditLimit()))
                .consumedAmount(toDouble(card.getConsumedAmount()))
                .availableCredit(toDouble(card.availableCredit()))
                .status(toApiCardStatus(card.getStatus()))
                .createdAt(toOffsetDateTime(card.getCreatedAt()))
                .updatedAt(toOffsetDateTime(card.getUpdatedAt()));
    }

    public CustomerType toModelCustomerType(com.nttdata.mscredit.generated.dto.CustomerType type) {
        return type == null ? null : CustomerType.valueOf(type.name());
    }

    public CreditProductStatus toModelProductStatus(com.nttdata.mscredit.generated.dto.CreditProductStatus status) {
        return status == null ? null : CreditProductStatus.valueOf(status.name());
    }

    public CreditCardStatus toModelCardStatus(com.nttdata.mscredit.generated.dto.CreditCardStatus status) {
        return status == null ? null : CreditCardStatus.valueOf(status.name());
    }

    public BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private com.nttdata.mscredit.generated.dto.CustomerType toApiCustomerType(CustomerType type) {
        return type == null ? null : com.nttdata.mscredit.generated.dto.CustomerType.valueOf(type.name());
    }

    private com.nttdata.mscredit.generated.dto.CreditProductStatus toApiProductStatus(CreditProductStatus status) {
        return status == null ? null : com.nttdata.mscredit.generated.dto.CreditProductStatus.valueOf(status.name());
    }

    private com.nttdata.mscredit.generated.dto.CreditCardStatus toApiCardStatus(CreditCardStatus status) {
        return status == null ? null : com.nttdata.mscredit.generated.dto.CreditCardStatus.valueOf(status.name());
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
