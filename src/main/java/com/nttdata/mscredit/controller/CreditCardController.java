package com.nttdata.mscredit.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.mscredit.generated.api.CreditCardsApi;
import com.nttdata.mscredit.generated.dto.CardChargeRequest;
import com.nttdata.mscredit.generated.dto.CreateCreditCardRequest;
import com.nttdata.mscredit.generated.dto.CreditCard;
import com.nttdata.mscredit.generated.dto.CreditCardStatus;
import com.nttdata.mscredit.generated.dto.PaymentRequest;
import com.nttdata.mscredit.mapper.CreditMapper;
import com.nttdata.mscredit.service.CreditCardService;
import com.nttdata.mscredit.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class CreditCardController implements CreditCardsApi {

    private final CreditCardService service;
    private final CreditMapper mapper;

    @Override
    public Mono<ResponseEntity<CreditCard>> createCreditCard(
            Mono<CreateCreditCardRequest> createCreditCardRequest,
            ServerWebExchange exchange) {
        return createCreditCardRequest.flatMap(request -> ReactiveBridge.toMono(service.issue(
                        request.getCustomerId(),
                        mapper.toModelCustomerType(request.getCustomerType()))))
                .map(card -> ResponseEntity
                        .created(URI.create("/api/v1/credit-cards/" + card.getId()))
                        .body(mapper.toCardDto(card)));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> getCreditCardById(String cardId, ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.get(cardId))
                .map(card -> ResponseEntity.ok(mapper.toCardDto(card)));
    }

    @Override
    public Mono<ResponseEntity<Flux<CreditCard>>> listCreditCards(
            String customerId,
            CreditCardStatus status,
            ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(
                Flux.from(service.list(customerId, mapper.toModelCardStatus(status)))
                        .map(mapper::toCardDto)));
    }

    @Override
    public Mono<ResponseEntity<Void>> cancelCreditCard(String cardId, ServerWebExchange exchange) {
        return ReactiveBridge.toVoid(service.cancel(cardId))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> chargeCreditCard(
            String cardId,
            Mono<CardChargeRequest> cardChargeRequest,
            ServerWebExchange exchange) {
        return cardChargeRequest.flatMap(request -> ReactiveBridge.toMono(service.charge(
                        cardId,
                        mapper.toBigDecimal(request.getAmount()))))
                .map(card -> ResponseEntity.ok(mapper.toCardDto(card)));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> payCreditCard(
            String cardId,
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest.flatMap(request -> ReactiveBridge.toMono(service.pay(
                        cardId,
                        mapper.toBigDecimal(request.getAmount()))))
                .map(card -> ResponseEntity.ok(mapper.toCardDto(card)));
    }
}
