package com.nttdata.mscredit.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("credit_cards")
public class CreditCard {

    @Id
    private String id;
    private String customerId;
    private CustomerType customerType;
    private String maskedNumber;
    private BigDecimal creditLimit;
    private BigDecimal consumedAmount;
    private CreditCardStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public BigDecimal availableCredit() {
        return creditLimit.subtract(consumedAmount);
    }
}
