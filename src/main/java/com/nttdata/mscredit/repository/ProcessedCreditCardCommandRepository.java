package com.nttdata.mscredit.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.nttdata.mscredit.model.ProcessedCreditCardCommand;

public interface ProcessedCreditCardCommandRepository
        extends ReactiveMongoRepository<ProcessedCreditCardCommand, String> {
}
