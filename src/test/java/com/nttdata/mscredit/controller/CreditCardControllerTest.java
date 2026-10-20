package com.nttdata.mscredit.controller;

import static com.nttdata.mscredit.support.Fixtures.card;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.generated.dto.CardChargeRequest;
import com.nttdata.mscredit.generated.dto.CreateCreditCardRequest;
import com.nttdata.mscredit.generated.dto.CreditCardStatus;
import com.nttdata.mscredit.generated.dto.CustomerType;
import com.nttdata.mscredit.generated.dto.PaymentRequest;
import com.nttdata.mscredit.mapper.CreditMapper;
import com.nttdata.mscredit.service.CreditCardService;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreditCardControllerTest {

    @Mock
    private CreditCardService service;
    private CreditCardController controller;

    @BeforeEach
    void setUp() {
        controller = new CreditCardController(service, new CreditMapper(clock()));
    }

    @Test
    void createReturnsCreatedWithLocationHeader() {
        CreateCreditCardRequest request = new CreateCreditCardRequest()
                .customerId("cust-1")
                .customerType(CustomerType.PERSONAL);
        when(service.issue("cust-1", com.nttdata.mscredit.model.CustomerType.PERSONAL))
                .thenReturn(Single.just(card("card-1", "5000.00", "0.00")));

        StepVerifier.create(controller.createCreditCard(Mono.just(request), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getHeaders().getLocation().getPath())
                            .isEqualTo("/api/v1/credit-cards/card-1");
                    assertThat(response.getBody().getAvailableCredit()).isEqualTo(5000.0);
                })
                .verifyComplete();
    }

    @Test
    void getReturnsOkWithCard() {
        when(service.get("card-1")).thenReturn(Single.just(card("card-1", "5000.00", "0.00")));

        StepVerifier.create(controller.getCreditCardById("card-1", null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getId()).isEqualTo("card-1");
                })
                .verifyComplete();
    }

    @Test
    void listStreamsCardsForCustomer() {
        when(service.list("cust-1", com.nttdata.mscredit.model.CreditCardStatus.ACTIVE))
                .thenReturn(Flowable.just(card("c1", "5000.00", "0.00")));

        StepVerifier.create(controller.listCreditCards("cust-1", CreditCardStatus.ACTIVE, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    StepVerifier.create(response.getBody()).expectNextCount(1).verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void cancelReturnsNoContent() {
        when(service.cancel("card-1")).thenReturn(Completable.complete());

        StepVerifier.create(controller.cancelCreditCard("card-1", null))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();
    }

    @Test
    void cancelPropagatesConflictForPendingConsumption() {
        when(service.cancel("card-1")).thenReturn(Completable.error(
                new BusinessException(HttpStatus.CONFLICT, "Credit card has pending consumption")));

        StepVerifier.create(controller.cancelCreditCard("card-1", null))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getStatus() == HttpStatus.CONFLICT)
                .verify();
    }

    @Test
    void chargeReturnsOkWithUpdatedCard() {
        when(service.charge("card-1", BigDecimal.valueOf(1200.0)))
                .thenReturn(Single.just(card("card-1", "5000.00", "1200.00")));

        StepVerifier.create(controller.chargeCreditCard("card-1",
                        Mono.just(new CardChargeRequest().amount(1200.0)), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getConsumedAmount()).isEqualTo(1200.0);
                })
                .verifyComplete();
    }

    @Test
    void payReturnsOkWithUpdatedCard() {
        when(service.pay("card-1", BigDecimal.valueOf(200.0)))
                .thenReturn(Single.just(card("card-1", "5000.00", "1000.00")));

        StepVerifier.create(controller.payCreditCard("card-1",
                        Mono.just(new PaymentRequest().amount(200.0)), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getConsumedAmount()).isEqualTo(1000.0);
                })
                .verifyComplete();
    }
}
