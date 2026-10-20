package com.nttdata.mscredit.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.nttdata.mscredit.event.CreditCardResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stored outcome of a card command, keyed by transaction id, so a redelivered command is answered without being
 * applied twice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("credit_card_commands")
public class ProcessedCreditCardCommand {

    @Id
    private String transactionId;
    private String creditCardId;
    private CreditCardResult.Outcome outcome;
    private String reason;
    private Instant occurredAt;
}
