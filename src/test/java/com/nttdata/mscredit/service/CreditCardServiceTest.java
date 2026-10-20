package com.nttdata.mscredit.service;

import static com.nttdata.mscredit.support.Fixtures.LIMITS;
import static com.nttdata.mscredit.support.Fixtures.card;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.event.CreditCardEvent;
import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.CreditCardStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.repository.CreditCardRepository;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository repository;
    @Mock
    private OverdueDebtChecker overdueDebtChecker;
    @Mock
    private CreditEventPublisher events;
    private CreditCardService service;

    @BeforeEach
    void setUp() {
        lenient().when(events.publish(anyString(), any(), any()))
                .thenReturn(Completable.complete());
        service = new CreditCardService(repository, overdueDebtChecker, LIMITS, clock(), events);
    }

    private void stubSave() {
        when(repository.save(any(CreditCard.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private static void assertBusinessError(TestObserver<?> observer, HttpStatus status) {
        observer.assertError(error -> error instanceof BusinessException business
                && business.getStatus() == status);
    }

    @Test
    void issuesPersonalCardWithConfiguredLimitAndMaskedNumber() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        stubSave();

        TestObserver<CreditCard> observer = service.issue("cust-1", CustomerType.PERSONAL).test();

        observer.assertNoErrors().assertComplete();
        CreditCard issued = observer.values().get(0);
        assertThat(issued.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(issued.getConsumedAmount()).isEqualByComparingTo("0");
        assertThat(issued.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
        assertThat(issued.getMaskedNumber()).matches("\\*{4} \\*{4} \\*{4} \\d{4}");
    }

    @Test
    void issuesBusinessCardWithHigherLimit() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        stubSave();

        TestObserver<CreditCard> observer = service.issue("cust-1", CustomerType.BUSINESS).test();

        observer.assertNoErrors().assertComplete();
        assertThat(observer.values().get(0).getCreditLimit()).isEqualByComparingTo("20000.00");
    }

    @Test
    void rejectsIssuanceWhenCustomerHasOverdueDebt() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(
                Completable.error(new BusinessException(HttpStatus.CONFLICT, "Customer has overdue debt")));

        assertBusinessError(service.issue("cust-1", CustomerType.PERSONAL).test(), HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void getReturnsExistingCard() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "0.00")));

        TestObserver<CreditCard> observer = service.get("card-1").test();

        observer.assertNoErrors().assertComplete();
        assertThat(observer.values().get(0).getId()).isEqualTo("card-1");
    }

    @Test
    void getReturnsNotFoundForUnknownCard() {
        when(repository.findById("missing")).thenReturn(Mono.empty());

        assertBusinessError(service.get("missing").test(), HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsCardsMatchingCustomerAndStatus() {
        when(repository.findAll(any(Example.class)))
                .thenReturn(Flux.just(card("c1", "5000.00", "0.00"), card("c2", "5000.00", "10.00")));

        service.list("cust-1", CreditCardStatus.ACTIVE).test()
                .assertValueCount(2)
                .assertComplete();
    }

    @Test
    void chargeIncreasesConsumedAmount() {
        CreditCard card = card("card-1", "5000.00", "0.00");
        when(repository.findById("card-1")).thenReturn(Mono.just(card));
        stubSave();

        service.charge("card-1", new BigDecimal("1200.00")).test().assertNoErrors().assertComplete();

        assertThat(card.getConsumedAmount()).isEqualByComparingTo("1200.00");
        assertThat(card.availableCredit()).isEqualByComparingTo("3800.00");
    }

    @Test
    void rejectsChargeAboveAvailableCredit() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "4500.00")));

        assertBusinessError(service.charge("card-1", new BigDecimal("600.00")).test(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void rejectsChargeOnCancelledCard() {
        when(repository.findById("card-1")).thenReturn(Mono.just(
                card("card-1", CustomerType.PERSONAL, "5000.00", "0.00", CreditCardStatus.CANCELLED)));

        assertBusinessError(service.charge("card-1", new BigDecimal("10.00")).test(), HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void rejectsNonPositiveCharge() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "0.00")));

        assertBusinessError(service.charge("card-1", BigDecimal.ZERO).test(), HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void partialPaymentReducesConsumedAmount() {
        CreditCard card = card("card-1", "5000.00", "1200.00");
        when(repository.findById("card-1")).thenReturn(Mono.just(card));
        stubSave();

        service.pay("card-1", new BigDecimal("200.00")).test().assertNoErrors().assertComplete();

        assertThat(card.getConsumedAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsPaymentAboveConsumedAmount() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "100.00")));

        assertBusinessError(service.pay("card-1", new BigDecimal("150.00")).test(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void rejectsNonPositivePayment() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "100.00")));

        assertBusinessError(service.pay("card-1", BigDecimal.ZERO).test(), HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void cancelSucceedsWhenNothingIsConsumed() {
        CreditCard card = card("card-1", "5000.00", "0.00");
        when(repository.findById("card-1")).thenReturn(Mono.just(card));
        stubSave();

        service.cancel("card-1").test().assertNoErrors().assertComplete();

        assertThat(card.getStatus()).isEqualTo(CreditCardStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentForCancelledCard() {
        when(repository.findById("card-1")).thenReturn(Mono.just(
                card("card-1", CustomerType.PERSONAL, "5000.00", "0.00", CreditCardStatus.CANCELLED)));

        service.cancel("card-1").test().assertNoErrors().assertComplete();

        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void cancelRejectsCardWithPendingConsumption() {
        when(repository.findById("card-1")).thenReturn(Mono.just(card("card-1", "5000.00", "100.00")));

        assertBusinessError(service.cancel("card-1").test(), HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditCard.class));
    }

    @Test
    void issuePublishesCreatedEvent() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        stubSave();

        service.issue("cust-1", CustomerType.PERSONAL).test().assertNoErrors().assertComplete();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(CreditCardEvent.TOPIC), any(), payload.capture());
        CreditCardEvent event = (CreditCardEvent) payload.getValue();
        assertThat(event.eventType()).isEqualTo(CreditCardEvent.EventType.CREATED);
        assertThat(event.customerId()).isEqualTo("cust-1");
    }

    @Test
    void issueSucceedsWhenEventPublishingFails() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        stubSave();
        when(events.publish(anyString(), any(), any()))
                .thenReturn(Completable.error(new IllegalStateException("kafka down")));

        service.issue("cust-1", CustomerType.PERSONAL).test().assertNoErrors().assertComplete();
    }

    @Test
    void cancelPublishesCancelledEventOnTransition() {
        CreditCard card = card("card-1", "5000.00", "0.00");
        when(repository.findById("card-1")).thenReturn(Mono.just(card));
        stubSave();

        service.cancel("card-1").test().assertNoErrors().assertComplete();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(CreditCardEvent.TOPIC), eq("card-1"), payload.capture());
        assertThat(((CreditCardEvent) payload.getValue()).eventType())
                .isEqualTo(CreditCardEvent.EventType.CANCELLED);
    }

    @Test
    void cancelDoesNotPublishWhenAlreadyCancelled() {
        when(repository.findById("card-1")).thenReturn(Mono.just(
                card("card-1", CustomerType.PERSONAL, "5000.00", "0.00", CreditCardStatus.CANCELLED)));

        service.cancel("card-1").test().assertNoErrors().assertComplete();

        verify(events, never()).publish(anyString(), any(), any());
    }
}
