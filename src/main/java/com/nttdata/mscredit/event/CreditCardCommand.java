package com.nttdata.mscredit.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command from ms-transaction. The transaction id is the idempotency key. The record shape is the approved
 * contract on {@code credit.card.commands}.
 */
public record CreditCardCommand(
        int schemaVersion,
        String transactionId,
        String creditCardId,
        Operation operation,
        BigDecimal amount,
        String payerDocumentType,
        String payerDocumentNumber,
        String merchant,
        Instant occurredAt) {

    public static final String TOPIC = "credit.card.commands";

    public enum Operation {
        CREDIT_PAYMENT,
        CARD_CONSUMPTION
    }
}
