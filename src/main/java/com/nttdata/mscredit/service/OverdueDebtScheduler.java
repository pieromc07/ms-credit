package com.nttdata.mscredit.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueDebtScheduler {

    private final OverdueDebtReconciler reconciler;

    @Scheduled(fixedDelayString = "${credit.overdue.reconciliation-interval:PT1M}")
    public void run() {
        try {
            reconciler.reconcile().blockingAwait();
        } catch (RuntimeException exception) {
            log.warn("Overdue debt reconciliation failed; it will retry on the next run", exception);
        }
    }
}
