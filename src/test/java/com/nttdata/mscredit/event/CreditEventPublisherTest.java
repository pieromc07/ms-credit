package com.nttdata.mscredit.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CreditEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private CreditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CreditEventPublisher(kafkaTemplate, JsonMapper.builder().build());
    }

    @Test
    void publishSendsJsonPayloadToTopicWithKey() {
        when(kafkaTemplate.send(eq(OverdueDebtEvent.TOPIC), eq("cust-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(OverdueDebtEvent.TOPIC, "cust-1", event()).test().assertComplete();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(OverdueDebtEvent.TOPIC), eq("cust-1"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"customerId\":\"cust-1\"")
                .contains("\"eventType\":\"OVERDUE_DEBT_DETECTED\"")
                .contains("\"schemaVersion\":1");
    }

    @Test
    void publishPropagatesBrokerFailure() {
        when(kafkaTemplate.send(eq(OverdueDebtEvent.TOPIC), eq("cust-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher.publish(OverdueDebtEvent.TOPIC, "cust-1", event())
                .test()
                .assertError(IllegalStateException.class);
    }

    @Test
    void publishDoesNotSendUntilSubscribed() {
        publisher.publish(OverdueDebtEvent.TOPIC, "cust-1", event());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    private static OverdueDebtEvent event() {
        return new OverdueDebtEvent(OverdueDebtEvent.SCHEMA_VERSION, "cust-1",
                OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED, Instant.parse("2026-05-10T10:00:00Z"));
    }
}
