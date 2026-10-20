package com.nttdata.mscredit.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.nttdata.mscredit.exception.BusinessException;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.repository.CreditProductRepository;
import com.nttdata.mscredit.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OverdueDebtChecker {

    private final CreditProductRepository repository;
    private final Clock clock;

    public Completable ensureNoOverdueDebt(String customerId) {
        return Completable.defer(() -> {
            LocalDate today = LocalDate.now(clock);
            CreditProduct probe = CreditProduct.builder().customerId(customerId).build();
            return ReactiveBridge.toFlowable(repository.findAll(Example.of(probe)))
                    .any(product -> product.isOverdue(today))
                    .flatMapCompletable(hasOverdue -> hasOverdue
                            ? Completable.error(new BusinessException(HttpStatus.CONFLICT,
                                    "Customer has overdue debt"))
                            : Completable.complete());
        });
    }
}
