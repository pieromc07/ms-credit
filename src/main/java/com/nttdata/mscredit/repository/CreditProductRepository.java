package com.nttdata.mscredit.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.nttdata.mscredit.model.CreditProduct;

public interface CreditProductRepository extends ReactiveMongoRepository<CreditProduct, String> {
}
