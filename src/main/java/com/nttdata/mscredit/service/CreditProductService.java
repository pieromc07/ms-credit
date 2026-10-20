package com.nttdata.mscredit.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.repository.CreditProductRepository;
import com.nttdata.mscredit.util.Amounts;
import com.nttdata.mscredit.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditProductService {

    private final CreditProductRepository repository;
    private final OverdueDebtChecker overdueDebtChecker;
    private final Clock clock;

    public Single<CreditProduct> create(String customerId, CustomerType customerType,
            BigDecimal amount, LocalDate dueDate) {
        return Single.defer(() -> {
            Amounts.requirePositive(amount);
            if (dueDate == null || dueDate.isBefore(LocalDate.now(clock))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Due date must not be in the past");
            }
            return overdueDebtChecker.ensureNoOverdueDebt(customerId)
                    .andThen(ensureNoActiveProductForPersonal(customerId, customerType))
                    .andThen(Single.defer(() -> {
                        Instant now = Instant.now(clock);
                        CreditProduct product = CreditProduct.builder()
                                .customerId(customerId)
                                .customerType(customerType)
                                .amount(amount)
                                .outstandingBalance(amount)
                                .dueDate(dueDate)
                                .status(CreditProductStatus.ACTIVE)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                        return ReactiveBridge.toSingle(repository.save(product));
                    }));
        });
    }

    public Single<CreditProduct> get(String productId) {
        return Single.defer(() -> ReactiveBridge.toMaybe(repository.findById(productId))
                .switchIfEmpty(Single.error(() -> notFound(productId))));
    }

    public Flowable<CreditProduct> list(String customerId, CreditProductStatus status) {
        return Flowable.defer(() -> {
            CreditProduct probe = CreditProduct.builder()
                    .customerId(customerId)
                    .status(status)
                    .build();
            return ReactiveBridge.toFlowable(repository.findAll(Example.of(probe)));
        });
    }

    public Single<CreditProduct> pay(String productId, BigDecimal amount) {
        return get(productId).flatMap(product -> {
            Amounts.requirePositive(amount);
            if (product.getStatus() == CreditProductStatus.PAID) {
                throw new BusinessException(HttpStatus.CONFLICT, "Credit product is already paid");
            }
            if (amount.compareTo(product.getOutstandingBalance()) > 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Payment exceeds outstanding balance");
            }
            BigDecimal remaining = product.getOutstandingBalance().subtract(amount);
            product.setOutstandingBalance(remaining);
            product.setStatus(remaining.signum() == 0 ? CreditProductStatus.PAID : CreditProductStatus.ACTIVE);
            product.setUpdatedAt(Instant.now(clock));
            return ReactiveBridge.toSingle(repository.save(product));
        });
    }

    private Completable ensureNoActiveProductForPersonal(String customerId, CustomerType customerType) {
        if (customerType != CustomerType.PERSONAL) {
            return Completable.complete();
        }
        return Completable.defer(() -> {
            CreditProduct probe = CreditProduct.builder()
                    .customerId(customerId)
                    .status(CreditProductStatus.ACTIVE)
                    .build();
            return ReactiveBridge.toFlowable(repository.findAll(Example.of(probe)))
                    .any(product -> true)
                    .flatMapCompletable(exists -> exists
                            ? Completable.error(new BusinessException(HttpStatus.CONFLICT,
                                    "Personal customer already has an active credit product"))
                            : Completable.complete());
        });
    }

    private BusinessException notFound(String productId) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Credit product not found: " + productId);
    }
}
