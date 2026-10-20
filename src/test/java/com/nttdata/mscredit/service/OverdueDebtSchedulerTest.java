package com.nttdata.mscredit.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.reactivex.rxjava3.core.Completable;

@ExtendWith(MockitoExtension.class)
class OverdueDebtSchedulerTest {

    @Mock
    private OverdueDebtReconciler reconciler;

    @Test
    void runReconcilesOnce() {
        when(reconciler.reconcile()).thenReturn(Completable.complete());

        new OverdueDebtScheduler(reconciler).run();

        verify(reconciler).reconcile();
    }

    @Test
    void runSwallowsFailureSoTheSchedulerKeepsRunning() {
        when(reconciler.reconcile()).thenReturn(Completable.error(new IllegalStateException("db down")));

        new OverdueDebtScheduler(reconciler).run();

        verify(reconciler).reconcile();
    }
}
