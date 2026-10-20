package com.nttdata.mscredit.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.nttdata.mscredit.model.OverdueDebtSnapshot;

public interface OverdueDebtSnapshotRepository extends ReactiveMongoRepository<OverdueDebtSnapshot, String> {
}
