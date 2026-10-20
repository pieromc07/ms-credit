package com.nttdata.mscredit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nttdata.mscredit.event.CreditEventPublisher;
import com.nttdata.mscredit.event.OverdueDebtEvent;
import com.nttdata.mscredit.model.CreditProduct;
import com.nttdata.mscredit.model.OverdueDebtSnapshot;
import com.nttdata.mscredit.repository.CreditProductRepository;
import com.nttdata.mscredit.repository.OverdueDebtSnapshotRepository;
import com.nttdata.mscredit.util.ReactiveBridge;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Compares the customers with overdue debt now against the last announced snapshot and publishes the transitions.
 * Events are published before the snapshot changes, so a failure is retried on the next run (at-least-once).
 */
@Component
@RequiredArgsConstructor
public class OverdueDebtReconciler {

    private final CreditProductRepository products;
    private final OverdueDebtSnapshotRepository snapshots;
    private final CreditEventPublisher publisher;
    private final Clock clock;

    public Completable reconcile() {
        return Completable.defer(() -> {
            LocalDate today = LocalDate.now(clock);
            return Single.zip(currentOverdueCustomers(today), announcedCustomers(), Reconciliation::new)
                    .flatMapCompletable(this::applyChanges);
        });
    }

    private Single<Set<String>> currentOverdueCustomers(LocalDate today) {
        return Single.defer(() -> ReactiveBridge.toFlowable(products.findAll())
                .filter(product -> product.isOverdue(today))
                .map(CreditProduct::getCustomerId)
                .collect(Collectors.toSet()));
    }

    private Single<Set<String>> announcedCustomers() {
        return Single.defer(() -> ReactiveBridge.toFlowable(snapshots.findAll())
                .map(OverdueDebtSnapshot::getCustomerId)
                .collect(Collectors.toSet()));
    }

    private Completable applyChanges(Reconciliation changes) {
        List<String> detected = difference(changes.current(), changes.previous());
        List<String> cleared = difference(changes.previous(), changes.current());
        return Flowable.fromIterable(detected).concatMapCompletable(this::markDetected)
                .andThen(Flowable.fromIterable(cleared).concatMapCompletable(this::markCleared));
    }

    private Completable markDetected(String customerId) {
        return publish(customerId, OverdueDebtEvent.EventType.OVERDUE_DEBT_DETECTED)
                .andThen(Completable.defer(() -> Completable.fromPublisher(
                        snapshots.save(new OverdueDebtSnapshot(customerId, Instant.now(clock))))));
    }

    private Completable markCleared(String customerId) {
        return publish(customerId, OverdueDebtEvent.EventType.OVERDUE_DEBT_CLEARED)
                .andThen(Completable.defer(() -> Completable.fromPublisher(snapshots.deleteById(customerId))));
    }

    private Completable publish(String customerId, OverdueDebtEvent.EventType type) {
        return publisher.publish(OverdueDebtEvent.TOPIC, customerId,
                new OverdueDebtEvent(OverdueDebtEvent.SCHEMA_VERSION, customerId, type, Instant.now(clock)));
    }

    private static List<String> difference(Set<String> from, Set<String> other) {
        return from.stream().filter(customerId -> !other.contains(customerId)).sorted().toList();
    }

    private record Reconciliation(Set<String> current, Set<String> previous) {
    }
}
