package com.nttdata.mscredit.event;

import java.time.Instant;

/**
 * Card lifecycle event consumed by ms-account and ms-transaction to keep local projections.
 * Proposed layout, not part of the approved contracts.
 */
public record CreditCardEvent(
        int schemaVersion,
        String creditCardId,
        String customerId,
        EventType eventType,
        Instant occurredAt) {

    public static final String TOPIC = "credit.card.events";
    public static final int SCHEMA_VERSION = 1;

    public enum EventType {
        CREATED,
        CANCELLED
    }
}
