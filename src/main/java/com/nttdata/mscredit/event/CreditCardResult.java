package com.nttdata.mscredit.event;

import java.time.Instant;

/**
 * Reply to a {@link CreditCardCommand}. The record shape is the approved contract on {@code credit.card.results}.
 */
public record CreditCardResult(
        int schemaVersion,
        String transactionId,
        String creditCardId,
        Outcome outcome,
        String reason,
        Instant occurredAt) {

    public static final String TOPIC = "credit.card.results";
    public static final int SCHEMA_VERSION = 1;

    public enum Outcome {
        OK,
        REJECTED
    }
}
