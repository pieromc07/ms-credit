package com.nttdata.mscredit.util;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveBridgeTest {

    @Test
    void toSingleEmitsMonoValue() {
        ReactiveBridge.toSingle(Mono.just("value")).test().assertValue("value").assertComplete();
    }

    @Test
    void toSingleFailsWhenMonoIsEmpty() {
        ReactiveBridge.toSingle(Mono.<String>empty()).test().assertError(NoSuchElementException.class);
    }

    @Test
    void toMaybeCompletesEmptyWhenMonoIsEmpty() {
        ReactiveBridge.toMaybe(Mono.<String>empty()).test().assertNoValues().assertComplete();
    }

    @Test
    void toMaybeEmitsMonoValue() {
        ReactiveBridge.toMaybe(Mono.just("value")).test().assertValue("value").assertComplete();
    }

    @Test
    void toFlowableEmitsAllFluxValues() {
        ReactiveBridge.toFlowable(Flux.just(1, 2)).test().assertValues(1, 2).assertComplete();
    }

    @Test
    void toMonoEmitsSingleValue() {
        StepVerifier.create(ReactiveBridge.toMono(Single.just("value")))
                .expectNext("value")
                .verifyComplete();
    }

    @Test
    void toVoidCompletesWithoutValue() {
        StepVerifier.create(ReactiveBridge.toVoid(Completable.complete()))
                .verifyComplete();
    }
}
