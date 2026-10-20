package com.nttdata.mscredit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
@Document("credit_products")
public class CreditProduct {

    @Id
    private String id;
    private String customerId;
    private CustomerType customerType;
    private BigDecimal amount;
    private BigDecimal outstandingBalance;
    private LocalDate dueDate;
    private CreditProductStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isOverdue(LocalDate today) {
        return status == CreditProductStatus.ACTIVE
                && outstandingBalance.signum() > 0
                && dueDate.isBefore(today);
    }
}
