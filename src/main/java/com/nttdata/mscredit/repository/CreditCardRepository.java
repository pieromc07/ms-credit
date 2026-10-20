package com.nttdata.mscredit.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.nttdata.mscredit.model.CreditCard;

public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {
}
