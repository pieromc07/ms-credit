package com.nttdata.mscredit.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.nttdata.mscredit.event.CreditCardCommand;
import com.nttdata.mscredit.event.CreditCardResult;
import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditCard;
import com.nttdata.mscredit.model.ProcessedCreditCardCommand;
import com.nttdata.mscredit.repository.ProcessedCreditCardCommandRepository;
import com.nttdata.mscredit.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Applies card commands once per transaction id. A redelivered command gets the stored outcome again instead of
 * being applied a second time.
 */
@Component
@RequiredArgsConstructor
public class CreditCardCommandHandler {

    private final CreditCardService creditCards;
    private final ProcessedCreditCardCommandRepository processed;
    private final CreditEventPublisher publisher;
    private final Clock clock;

    public Completable handle(CreditCardCommand command) {
        return Single.defer(() -> ReactiveBridge.toMaybe(processed.findById(command.transactionId()))
                        .map(CreditCardCommandHandler::toResult)
                        .switchIfEmpty(Single.defer(() -> process(command))))
                .flatMapCompletable(result -> publisher.publish(CreditCardResult.TOPIC,
                        result.creditCardId(), result));
    }

    private Single<CreditCardResult> process(CreditCardCommand command) {
        return apply(command)
                .map(card -> result(command, CreditCardResult.Outcome.OK, null))
                .onErrorResumeNext(error -> {
                    if (error instanceof BusinessException business) {
                        return Single.just(result(command, CreditCardResult.Outcome.REJECTED, business.getMessage()));
                    }
                    return Single.error(error);
                })
                .flatMap(this::remember);
    }

    private Single<CreditCard> apply(CreditCardCommand command) {
        return switch (command.operation()) {
            case CREDIT_PAYMENT -> creditCards.pay(command.creditCardId(), command.amount());
            case CARD_CONSUMPTION -> creditCards.charge(command.creditCardId(), command.amount());
        };
    }

    private Single<CreditCardResult> remember(CreditCardResult result) {
        return Single.defer(() -> ReactiveBridge.toSingle(processed.save(toDocument(result))))
                .map(saved -> result);
    }

    private CreditCardResult result(CreditCardCommand command, CreditCardResult.Outcome outcome, String reason) {
        return new CreditCardResult(CreditCardResult.SCHEMA_VERSION, command.transactionId(),
                command.creditCardId(), outcome, reason, Instant.now(clock));
    }

    private static ProcessedCreditCardCommand toDocument(CreditCardResult result) {
        return ProcessedCreditCardCommand.builder()
                .transactionId(result.transactionId())
                .creditCardId(result.creditCardId())
                .outcome(result.outcome())
                .reason(result.reason())
                .occurredAt(result.occurredAt())
                .build();
    }

    private static CreditCardResult toResult(ProcessedCreditCardCommand document) {
        return new CreditCardResult(CreditCardResult.SCHEMA_VERSION, document.getTransactionId(),
                document.getCreditCardId(), document.getOutcome(), document.getReason(), document.getOccurredAt());
    }
}
