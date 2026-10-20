package com.nttdata.mscredit.service;

import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static com.nttdata.mscredit.support.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;

import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.repository.CreditProductRepository;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreditProductServiceTest {

    @Mock
    private CreditProductRepository repository;
    @Mock
    private OverdueDebtChecker overdueDebtChecker;
    private CreditProductService service;

    @BeforeEach
    void setUp() {
        service = new CreditProductService(repository, overdueDebtChecker, clock());
    }

    private void stubSave() {
        when(repository.save(any(CreditProduct.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private static void assertBusinessError(TestObserver<?> observer, HttpStatus status) {
        observer.assertError(error -> error instanceof BusinessException business
                && business.getStatus() == status);
    }

    @Test
    void createsPersonalProductWithFullOutstandingBalance() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        when(repository.findAll(any(Example.class))).thenReturn(Flux.empty());
        stubSave();

        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.PERSONAL,
                new BigDecimal("1000.00"), TODAY.plusMonths(1)).test();

        observer.assertNoErrors().assertComplete();
        CreditProduct created = observer.values().get(0);
        assertThat(created.getStatus()).isEqualTo(CreditProductStatus.ACTIVE);
        assertThat(created.getOutstandingBalance()).isEqualByComparingTo("1000.00");
        assertThat(created.getDueDate()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void businessCustomersMayHaveSeveralProducts() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        stubSave();

        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.BUSINESS,
                new BigDecimal("500.00"), TODAY.plusMonths(2)).test();

        observer.assertNoErrors().assertComplete();
        verify(repository, never()).findAll(any(Example.class));
    }

    @Test
    void rejectsPersonalCustomerWithActiveProduct() {
        CreditProduct existing = product("p0", CustomerType.PERSONAL, "200.00",
                TODAY.plusDays(5), CreditProductStatus.ACTIVE);
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(Completable.complete());
        when(repository.findAll(any(Example.class))).thenReturn(Flux.just(existing));

        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.PERSONAL,
                new BigDecimal("1000.00"), TODAY.plusMonths(1)).test();

        assertBusinessError(observer, HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditProduct.class));
    }

    @Test
    void rejectsCustomerWithOverdueDebtBeforeSaving() {
        when(overdueDebtChecker.ensureNoOverdueDebt("cust-1")).thenReturn(
                Completable.error(new BusinessException(HttpStatus.CONFLICT, "Customer has overdue debt")));

        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.BUSINESS,
                new BigDecimal("1000.00"), TODAY.plusMonths(1)).test();

        assertBusinessError(observer, HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditProduct.class));
    }

    @Test
    void rejectsNonPositiveAmountBeforeCallingCollaborators() {
        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.PERSONAL,
                BigDecimal.ZERO, TODAY.plusMonths(1)).test();

        assertBusinessError(observer, HttpStatus.BAD_REQUEST);
        verifyNoInteractions(overdueDebtChecker, repository);
    }

    @Test
    void rejectsDueDateInThePast() {
        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.PERSONAL,
                new BigDecimal("1000.00"), TODAY.minusDays(1)).test();

        assertBusinessError(observer, HttpStatus.BAD_REQUEST);
        verifyNoInteractions(overdueDebtChecker, repository);
    }

    @Test
    void rejectsMissingDueDate() {
        TestObserver<CreditProduct> observer = service.create("cust-1", CustomerType.PERSONAL,
                new BigDecimal("1000.00"), null).test();

        assertBusinessError(observer, HttpStatus.BAD_REQUEST);
        verifyNoInteractions(overdueDebtChecker, repository);
    }

    @Test
    void getReturnsExistingProduct() {
        when(repository.findById("p1")).thenReturn(Mono.just(product("p1", "1000.00")));

        TestObserver<CreditProduct> observer = service.get("p1").test();

        observer.assertNoErrors().assertComplete();
        assertThat(observer.values().get(0).getId()).isEqualTo("p1");
    }

    @Test
    void getReturnsNotFoundForUnknownProduct() {
        when(repository.findById("missing")).thenReturn(Mono.empty());

        assertBusinessError(service.get("missing").test(), HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsProductsMatchingCustomerAndStatus() {
        when(repository.findAll(any(Example.class)))
                .thenReturn(Flux.just(product("p1", "100.00"), product("p2", "200.00")));

        service.list("cust-1", CreditProductStatus.ACTIVE).test()
                .assertValueCount(2)
                .assertComplete();
    }

    @Test
    void partialPaymentReducesOutstandingBalanceAndStaysActive() {
        CreditProduct product = product("p1", "1000.00");
        when(repository.findById("p1")).thenReturn(Mono.just(product));
        stubSave();

        TestObserver<CreditProduct> observer = service.pay("p1", new BigDecimal("400.00")).test();

        observer.assertNoErrors().assertComplete();
        assertThat(product.getOutstandingBalance()).isEqualByComparingTo("600.00");
        assertThat(product.getStatus()).isEqualTo(CreditProductStatus.ACTIVE);
    }

    @Test
    void fullPaymentMarksProductAsPaid() {
        CreditProduct product = product("p1", "1000.00");
        when(repository.findById("p1")).thenReturn(Mono.just(product));
        stubSave();

        service.pay("p1", new BigDecimal("1000.00")).test().assertNoErrors().assertComplete();

        assertThat(product.getOutstandingBalance()).isEqualByComparingTo("0");
        assertThat(product.getStatus()).isEqualTo(CreditProductStatus.PAID);
    }

    @Test
    void rejectsPaymentOnPaidProduct() {
        CreditProduct paid = product("p1", CustomerType.PERSONAL, "0.00", TODAY.minusDays(3),
                CreditProductStatus.PAID);
        when(repository.findById("p1")).thenReturn(Mono.just(paid));

        assertBusinessError(service.pay("p1", new BigDecimal("10.00")).test(), HttpStatus.CONFLICT);
        verify(repository, never()).save(any(CreditProduct.class));
    }

    @Test
    void rejectsPaymentAboveOutstandingBalance() {
        when(repository.findById("p1")).thenReturn(Mono.just(product("p1", "100.00")));

        assertBusinessError(service.pay("p1", new BigDecimal("150.00")).test(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        verify(repository, never()).save(any(CreditProduct.class));
    }

    @Test
    void rejectsNonPositivePayment() {
        when(repository.findById("p1")).thenReturn(Mono.just(product("p1", "100.00")));

        assertBusinessError(service.pay("p1", BigDecimal.ZERO).test(), HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(CreditProduct.class));
    }

    @Test
    void payReturnsNotFoundForUnknownProduct() {
        when(repository.findById("missing")).thenReturn(Mono.empty());

        assertBusinessError(service.pay("missing", new BigDecimal("10.00")).test(), HttpStatus.NOT_FOUND);
    }
}
