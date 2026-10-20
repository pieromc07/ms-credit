package com.nttdata.mscredit.service;

import static com.nttdata.mscredit.support.Fixtures.NOW;
import static com.nttdata.mscredit.support.Fixtures.TODAY;
import static com.nttdata.mscredit.support.Fixtures.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.event.OverdueDebtEvent;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.CreditProductStatus;
import com.nttdata.mscredit.model.CustomerType;
import com.nttdata.mscredit.model.OverdueDebtSnapshot;
import com.nttdata.mscredit.repository.CreditProductRepository;
import com.nttdata.mscredit.repository.OverdueDebtSnapshotRepository;

import io.reactivex.rxjava3.core.Completable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OverdueDebtReconcilerTest {

    @Mock
    private CreditProductRepository products;
    @Mock
    private OverdueDebtSnapshotRepository snapshots;
    @Mock
    private CreditEventPublisher publisher;

    private OverdueDebtReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new OverdueDebtReconciler(products, snapshots, publisher, clock());
    }

    @Test
    void publishesDetectedAndStoresSnapshotForNewlyOverdueCustomer() {
        when(products.findAll()).thenReturn(Flux.just(product("cust-2", TODAY.minusDays(1))));
        when(snapshots.findAll()).thenReturn(Flux.empty());
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());
        when(snapshots.save(any(OverdueDebtSnapshot.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        reconciler.reconcile().test().assertComplete();

        assertThat(eventTypes()).containsExactly(OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED);
        ArgumentCaptor<OverdueDebtSnapshot> saved = ArgumentCaptor.forClass(OverdueDebtSnapshot.class);
        verify(snapshots).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo("cust-2");
        assertThat(saved.getValue().getDetectedAt()).isEqualTo(NOW);
    }

    @Test
    void publishesClearedAndRemovesSnapshotWhenDebtIsPaid() {
        when(products.findAll()).thenReturn(Flux.empty());
        when(snapshots.findAll()).thenReturn(Flux.just(new OverdueDebtSnapshot("cust-3", NOW)));
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Completable.complete());
        when(snapshots.deleteById("cust-3")).thenReturn(Mono.empty());

        reconciler.reconcile().test().assertComplete();

        assertThat(eventTypes()).containsExactly(OverdueDebtEvent.EventType.OVERDUE_DEBT_CLEARED);
        verify(snapshots).deleteById("cust-3");
    }

    @Test
    void publishesNothingWhenStateIsUnchanged() {
        when(products.findAll()).thenReturn(Flux.just(product("cust-2", TODAY.minusDays(1))));
        when(snapshots.findAll()).thenReturn(Flux.just(new OverdueDebtSnapshot("cust-2", NOW)));

        reconciler.reconcile().test().assertComplete();

        verify(publisher, never()).publish(anyString(), anyString(), any());
        verify(snapshots, never()).save(any());
        verify(snapshots, never()).deleteById(anyString());
    }

    @Test
    void ignoresProductsThatAreNotOverdue() {
        when(products.findAll()).thenReturn(Flux.just(product("cust-4", TODAY.plusDays(10))));
        when(snapshots.findAll()).thenReturn(Flux.empty());

        reconciler.reconcile().test().assertComplete();

        verify(publisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void failedPublishLeavesSnapshotUnchangedSoNextRunRetries() {
        when(products.findAll()).thenReturn(Flux.just(product("cust-2", TODAY.minusDays(1))));
        when(snapshots.findAll()).thenReturn(Flux.empty());
        when(publisher.publish(anyString(), anyString(), any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        reconciler.reconcile().test().assertError(IllegalStateException.class);

        verify(snapshots, never()).save(any());
    }

    private List<OverdueDebtEvent.EventType> eventTypes() {
        ArgumentCaptor<OverdueDebtEvent> events = ArgumentCaptor.forClass(OverdueDebtEvent.class);
        verify(publisher, atLeastOnce()).publish(eq(OverdueDebtEvent.TOPIC), anyString(), events.capture());
        return events.getAllValues().stream().map(OverdueDebtEvent::eventType).toList();
    }

    private static CreditProduct product(String customerId, LocalDate dueDate) {
        return CreditProduct.builder()
                .id("prod-" + customerId)
                .customerId(customerId)
                .customerType(CustomerType.PERSONAL)
                .amount(new BigDecimal("1000.00"))
                .outstandingBalance(new BigDecimal("250.00"))
                .dueDate(dueDate)
                .status(CreditProductStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
