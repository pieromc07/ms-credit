package com.nttdata.mscredit.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nttdata.mscredit.service.CreditCardCommandHandler;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka edge for card commands. Failures are rethrown so Kafka redelivers the record.
 */
@Component
@RequiredArgsConstructor
public class CreditCardCommandListener {

    private final CreditCardCommandHandler handler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = CreditCardCommand.TOPIC, groupId = "ms-credit-commands")
    public void onCreditCardCommand(String payload) {
        CreditCardCommand command = objectMapper.readValue(payload, CreditCardCommand.class);
        handler.handle(command).blockingAwait();
    }
}
