package com.nttdata.mscredit.controller;

import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static com.nttdata.mscredit.support.Fixtures.product;
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
import com.nttdata.mscredit.generated.dto.CreateCreditProductRequest;
import com.nttdata.mscredit.generated.dto.CreditProductStatus;
import com.nttdata.mscredit.generated.dto.CustomerType;
import com.nttdata.mscredit.generated.dto.PaymentRequest;
import com.nttdata.mscredit.mapper.CreditMapper;
import com.nttdata.mscredit.service.CreditProductService;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreditProductControllerTest {

    @Mock
    private CreditProductService service;
    private CreditProductController controller;

    @BeforeEach
    void setUp() {
        controller = new CreditProductController(service, new CreditMapper(clock()));
    }

    @Test
    void createReturnsCreatedWithLocationHeader() {
        CreateCreditProductRequest request = new CreateCreditProductRequest()
                .customerId("cust-1")
                .customerType(CustomerType.PERSONAL)
                .amount(1000.0)
                .dueDate(TODAY.plusMonths(1));
        when(service.create("cust-1", com.nttdata.mscredit.model.CustomerType.PERSONAL,
                BigDecimal.valueOf(1000.0), TODAY.plusMonths(1)))
                .thenReturn(Single.just(product("p1", "1000.00")));

        StepVerifier.create(controller.createCreditProduct(Mono.just(request), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getHeaders().getLocation().getPath())
                            .isEqualTo("/api/v1/credit-products/p1");
                    assertThat(response.getBody().getId()).isEqualTo("p1");
                })
                .verifyComplete();
    }

    @Test
    void getReturnsOkWithProduct() {
        when(service.get("p1")).thenReturn(Single.just(product("p1", "1000.00")));

        StepVerifier.create(controller.getCreditProductById("p1", null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getId()).isEqualTo("p1");
                })
                .verifyComplete();
    }

    @Test
    void getPropagatesNotFound() {
        when(service.get("missing")).thenReturn(Single.error(
                new BusinessException(HttpStatus.NOT_FOUND, "Credit product not found: missing")));

        StepVerifier.create(controller.getCreditProductById("missing", null))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getStatus() == HttpStatus.NOT_FOUND)
                .verify();
    }

    @Test
    void listStreamsProductsForCustomer() {
        when(service.list("cust-1", com.nttdata.mscredit.model.CreditProductStatus.ACTIVE))
                .thenReturn(Flowable.just(product("p1", "100.00"), product("p2", "200.00")));

        StepVerifier.create(controller.listCreditProducts("cust-1", CreditProductStatus.ACTIVE, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    StepVerifier.create(response.getBody()).expectNextCount(2).verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void payReturnsUpdatedProduct() {
        when(service.pay("p1", BigDecimal.valueOf(400.0)))
                .thenReturn(Single.just(product("p1", "600.00")));

        StepVerifier.create(controller.payCreditProduct("p1",
                        Mono.just(new PaymentRequest().amount(400.0)), null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getOutstandingBalance()).isEqualTo(600.0);
                })
                .verifyComplete();
    }
}
