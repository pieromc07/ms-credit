package com.nttdata.mscredit.service;

import static com.nttdata.mscredit.support.Fixtures.NOW;
import static com.nttdata.mscredit.support.Fixtures.card;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.event.CreditCardCommand;
import com.nttdata.mscredit.event.CreditCardResult;
import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.ProcessedCreditCardCommand;
import com.nttdata.mscredit.repository.ProcessedCreditCardCommandRepository;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreditCardCommandHandlerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private CreditCardService creditCards;
    @Mock
    private ProcessedCreditCardCommandRepository processed;
    @Mock
    private CreditEventPublisher publisher;

    private CreditCardCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreditCardCommandHandler(creditCards, processed, publisher, clock());
    }

    @Test
    void appliesChargeAndPublishesOkResult() {
        when(processed.findById("tx-1")).thenReturn(Mono.empty());
        when(creditCards.charge("card-1", AMOUNT)).thenReturn(Single.just(card("card-1", "5000.00", "100.00")));
        when(processed.save(any(ProcessedCreditCardCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());

        handler.handle(command("tx-1", CreditCardCommand.Operation.CARD_CONSUMPTION)).test().assertComplete();

        ArgumentCaptor<CreditCardResult> result = resultCaptor();
        assertThat(result.getValue().outcome()).isEqualTo(CreditCardResult.Outcome.OK);
        assertThat(result.getValue().reason()).isNull();
        assertThat(result.getValue().transactionId()).isEqualTo("tx-1");
        assertThat(result.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void appliesPaymentForCreditPaymentOperation() {
        when(processed.findById("tx-2")).thenReturn(Mono.empty());
        when(creditCards.pay("card-1", AMOUNT)).thenReturn(Single.just(card("card-1", "5000.00", "0.00")));
        when(processed.save(any(ProcessedCreditCardCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());

        handler.handle(command("tx-2", CreditCardCommand.Operation.CREDIT_PAYMENT)).test().assertComplete();

        verify(creditCards).pay("card-1", AMOUNT);
        verify(creditCards, never()).charge(anyString(), any());
    }

    @Test
    void rejectsBusinessViolationAndRemembersRejection() {
        when(processed.findById("tx-3")).thenReturn(Mono.empty());
        when(creditCards.charge("card-1", AMOUNT)).thenReturn(Single.error(
                new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Credit limit exceeded")));
        when(processed.save(any(ProcessedCreditCardCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());

        handler.handle(command("tx-3", CreditCardCommand.Operation.CARD_CONSUMPTION)).test().assertComplete();

        ArgumentCaptor<CreditCardResult> result = resultCaptor();
        assertThat(result.getValue().outcome()).isEqualTo(CreditCardResult.Outcome.REJECTED);
        assertThat(result.getValue().reason()).isEqualTo("Credit limit exceeded");
        ArgumentCaptor<ProcessedCreditCardCommand> stored = ArgumentCaptor.forClass(ProcessedCreditCardCommand.class);
        verify(processed).save(stored.capture());
        assertThat(stored.getValue().getOutcome()).isEqualTo(CreditCardResult.Outcome.REJECTED);
    }

    @Test
    void redeliveredCommandRepublishesStoredOutcomeWithoutApplyingAgain() {
        ProcessedCreditCardCommand stored = ProcessedCreditCardCommand.builder()
                .transactionId("tx-4")
                .creditCardId("card-1")
                .outcome(CreditCardResult.Outcome.OK)
                .occurredAt(NOW)
                .build();
        when(processed.findById("tx-4")).thenReturn(Mono.just(stored));
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());

        handler.handle(command("tx-4", CreditCardCommand.Operation.CARD_CONSUMPTION)).test().assertComplete();

        verify(creditCards, never()).charge(anyString(), any());
        verify(processed, never()).save(any());
        ArgumentCaptor<CreditCardResult> result = resultCaptor();
        assertThat(result.getValue().outcome()).isEqualTo(CreditCardResult.Outcome.OK);
        assertThat(result.getValue().transactionId()).isEqualTo("tx-4");
    }

    @Test
    void unexpectedFailureIsPropagatedWithoutStoringOrPublishing() {
        when(processed.findById("tx-5")).thenReturn(Mono.empty());
        when(creditCards.charge("card-1", AMOUNT)).thenReturn(Single.error(new IllegalStateException("db down")));

        handler.handle(command("tx-5", CreditCardCommand.Operation.CARD_CONSUMPTION))
                .test()
                .assertError(IllegalStateException.class);

        verify(processed, never()).save(any());
        verify(publisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void publishFailurePropagatesSoTheCommandIsRedelivered() {
        when(processed.findById("tx-6")).thenReturn(Mono.empty());
        when(creditCards.charge("card-1", AMOUNT)).thenReturn(Single.just(card("card-1", "5000.00", "100.00")));
        when(processed.save(any(ProcessedCreditCardCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(publisher.publish(anyString(), anyString(), any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        handler.handle(command("tx-6", CreditCardCommand.Operation.CARD_CONSUMPTION))
                .test()
                .assertError(IllegalStateException.class);
    }

    private static CreditCardCommand command(String transactionId, CreditCardCommand.Operation operation) {
        return new CreditCardCommand(1, transactionId, "card-1", operation, AMOUNT,
                "DNI", "12345678", "Merchant", NOW);
    }

    private ArgumentCaptor<CreditCardResult> resultCaptor() {
        ArgumentCaptor<CreditCardResult> result = ArgumentCaptor.forClass(CreditCardResult.class);
        verify(publisher).publish(eq(CreditCardResult.TOPIC), eq("card-1"), result.capture());
        return result;
    }
}
