package com.nttdata.mscredit.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.mscredit.service.CreditCardCommandHandler;

import io.reactivex.rxjava3.core.Completable;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CreditCardCommandListenerTest {

    private static final String PAYLOAD = """
            {"schemaVersion":1,"transactionId":"tx-1","creditCardId":"card-1",
             "operation":"CARD_CONSUMPTION","amount":100.00,"payerDocumentType":"DNI",
             "payerDocumentNumber":"12345678","merchant":"Shop","occurredAt":"2026-05-10T10:00:00Z"}
            """;

    @Mock
    private CreditCardCommandHandler handler;

    private CreditCardCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new CreditCardCommandListener(handler, JsonMapper.builder().build());
    }

    @Test
    void parsesPayloadAndDelegatesToHandler() {
        when(handler.handle(any(CreditCardCommand.class))).thenReturn(Completable.complete());

        listener.onCreditCardCommand(PAYLOAD);

        ArgumentCaptor<CreditCardCommand> command = ArgumentCaptor.forClass(CreditCardCommand.class);
        verify(handler).handle(command.capture());
        assertThat(command.getValue().transactionId()).isEqualTo("tx-1");
        assertThat(command.getValue().creditCardId()).isEqualTo("card-1");
        assertThat(command.getValue().operation()).isEqualTo(CreditCardCommand.Operation.CARD_CONSUMPTION);
        assertThat(command.getValue().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void rethrowsHandlerFailureSoKafkaRedelivers() {
        when(handler.handle(any(CreditCardCommand.class)))
                .thenReturn(Completable.error(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> listener.onCreditCardCommand(PAYLOAD));
    }

    @Test
    void malformedPayloadFailsTheRecord() {
        assertThrows(RuntimeException.class, () -> listener.onCreditCardCommand("{not json"));
    }
}
