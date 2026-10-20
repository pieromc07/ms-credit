package com.nttdata.mscredit.service;

import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static com.nttdata.mscredit.support.Fixtures.product;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.repository.CreditProductRepository;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class OverdueDebtCheckerTest {

    @Mock
    private CreditProductRepository repository;
    private OverdueDebtChecker checker;

    @BeforeEach
    void setUp() {
        checker = new OverdueDebtChecker(repository, clock());
    }

    @Test
    void completesWhenCustomerHasNoProducts() {
        when(repository.findAll(any(Example.class))).thenReturn(Flux.empty());

        checker.ensureNoOverdueDebt("cust-1").test().assertComplete();
    }

    @Test
    void completesWhenProductsAreCurrentOrPaid() {
        when(repository.findAll(any(Example.class))).thenReturn(Flux.just(
                product("p1", CustomerType.PERSONAL, "300.00", TODAY.plusDays(10), CreditProductStatus.ACTIVE),
                product("p2", CustomerType.PERSONAL, "0.00", TODAY.minusDays(10), CreditProductStatus.PAID)));

        checker.ensureNoOverdueDebt("cust-1").test().assertComplete();
    }

    @Test
    void rejectsCustomerWithOverdueDebt() {
        when(repository.findAll(any(Example.class))).thenReturn(Flux.just(
                product("p1", CustomerType.PERSONAL, "300.00", TODAY.minusDays(1), CreditProductStatus.ACTIVE)));

        checker.ensureNoOverdueDebt("cust-1").test().assertError(error -> error instanceof BusinessException
                && ((BusinessException) error).getStatus() == HttpStatus.CONFLICT);
    }
}
