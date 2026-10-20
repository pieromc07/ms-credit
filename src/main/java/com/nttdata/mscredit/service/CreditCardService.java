package com.nttdata.mscredit.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.nttdata.mscredit.config.CardLimitProperties;
import com.nttdata.mscredit.event.CreditCardEvent;
import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.CreditCardStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.repository.CreditCardRepository;
import com.nttdata.mscredit.util.Amounts;
import com.nttdata.mscredit.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditCardService {

    private final CreditCardRepository repository;
    private final OverdueDebtChecker overdueDebtChecker;
    private final CardLimitProperties limits;
    private final Clock clock;
    private final CreditEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public Single<CreditCard> issue(String customerId, CustomerType customerType) {
        return Single.defer(() -> overdueDebtChecker.ensureNoOverdueDebt(customerId)
                .andThen(Single.defer(() -> {
                    Instant now = Instant.now(clock);
                    CreditCard card = CreditCard.builder()
                            .customerId(customerId)
                            .customerType(customerType)
                            .maskedNumber(maskedNumber())
                            .creditLimit(limits.limitFor(customerType))
                            .consumedAmount(BigDecimal.ZERO)
                            .status(CreditCardStatus.ACTIVE)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return ReactiveBridge.toSingle(repository.save(card))
                            .flatMap(saved -> publishCardEvent(saved, CreditCardEvent.EventType.CREATED)
                                    .andThen(Single.just(saved)));
                })));
    }

    public Single<CreditCard> get(String cardId) {
        return Single.defer(() -> ReactiveBridge.toMaybe(repository.findById(cardId))
                .switchIfEmpty(Single.error(() -> notFound(cardId))));
    }

    public Flowable<CreditCard> list(String customerId, CreditCardStatus status) {
        return Flowable.defer(() -> {
            CreditCard probe = CreditCard.builder()
                    .customerId(customerId)
                    .status(status)
                    .build();
            return ReactiveBridge.toFlowable(repository.findAll(Example.of(probe)));
        });
    }

    public Single<CreditCard> charge(String cardId, BigDecimal amount) {
        return get(cardId).flatMap(card -> {
            Amounts.requirePositive(amount);
            if (card.getStatus() == CreditCardStatus.CANCELLED) {
                throw new BusinessException(HttpStatus.CONFLICT, "Credit card is cancelled");
            }
            if (amount.compareTo(card.availableCredit()) > 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Charge exceeds available credit");
            }
            card.setConsumedAmount(card.getConsumedAmount().add(amount));
            card.setUpdatedAt(Instant.now(clock));
            return ReactiveBridge.toSingle(repository.save(card));
        });
    }

    public Single<CreditCard> pay(String cardId, BigDecimal amount) {
        return get(cardId).flatMap(card -> {
            Amounts.requirePositive(amount);
            if (amount.compareTo(card.getConsumedAmount()) > 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Payment exceeds consumed amount");
            }
            card.setConsumedAmount(card.getConsumedAmount().subtract(amount));
            card.setUpdatedAt(Instant.now(clock));
            return ReactiveBridge.toSingle(repository.save(card));
        });
    }

    public Completable cancel(String cardId) {
        return get(cardId).flatMapCompletable(card -> {
            if (card.getStatus() == CreditCardStatus.CANCELLED) {
                return Completable.complete();
            }
            if (card.getConsumedAmount().signum() > 0) {
                return Completable.error(new BusinessException(HttpStatus.CONFLICT,
                        "Credit card has pending consumption"));
            }
            card.setStatus(CreditCardStatus.CANCELLED);
            card.setUpdatedAt(Instant.now(clock));
            return ReactiveBridge.toSingle(repository.save(card))
                    .ignoreElement()
                    .andThen(publishCardEvent(card, CreditCardEvent.EventType.CANCELLED));
        });
    }

    /** Event loss is logged and does not fail the request; the card state is already saved. */
    private Completable publishCardEvent(CreditCard card, CreditCardEvent.EventType type) {
        return events.publish(CreditCardEvent.TOPIC, card.getId(), new CreditCardEvent(
                        CreditCardEvent.SCHEMA_VERSION, card.getId(), card.getCustomerId(), type, Instant.now(clock)))
                .doOnError(error -> log.warn("Could not publish {} for credit card {}", type, card.getId(), error))
                .onErrorComplete();
    }

    private String maskedNumber() {
        return String.format("**** **** **** %04d", random.nextInt(10_000));
    }

    private BusinessException notFound(String cardId) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Credit card not found: " + cardId);
    }
}
