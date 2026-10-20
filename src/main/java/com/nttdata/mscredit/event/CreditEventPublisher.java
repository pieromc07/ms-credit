package com.nttdata.mscredit.event;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import io.reactivex.rxjava3.core.Completable;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes credit replies and events. Failures are propagated so each caller decides whether to retry or swallow.
 */
@Component
public class CreditEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CreditEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Completable publish(String topic, String key, Object event) {
        return Completable.defer(() -> {
            String payload = objectMapper.writeValueAsString(event);
            CompletableFuture<?> sent = kafkaTemplate.send(topic, key, payload);
            return Completable.create(emitter -> sent.whenComplete((result, error) -> {
                if (error == null) {
                    emitter.onComplete();
                } else {
                    emitter.onError(error);
                }
            }));
        });
    }
}
