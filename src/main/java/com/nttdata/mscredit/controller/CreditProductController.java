package com.nttdata.mscredit.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.nttdata.mscredit.generated.api.CreditProductsApi;
import com.nttdata.mscredit.generated.dto.CreateCreditProductRequest;
import com.nttdata.mscredit.generated.dto.CreditProduct;
import com.nttdata.mscredit.generated.dto.CreditProductStatus;
import com.nttdata.mscredit.generated.dto.PaymentRequest;
import com.nttdata.mscredit.mapper.CreditMapper;
import com.nttdata.mscredit.service.CreditProductService;
import com.nttdata.mscredit.util.ReactiveBridge;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class CreditProductController implements CreditProductsApi {

    private final CreditProductService service;
    private final CreditMapper mapper;

    @Override
    public Mono<ResponseEntity<CreditProduct>> createCreditProduct(
            Mono<CreateCreditProductRequest> createCreditProductRequest,
            ServerWebExchange exchange) {
        return createCreditProductRequest.flatMap(request -> ReactiveBridge.toMono(service.create(
                        request.getCustomerId(),
                        mapper.toModelCustomerType(request.getCustomerType()),
                        mapper.toBigDecimal(request.getAmount()),
                        request.getDueDate())))
                .map(product -> ResponseEntity
                        .created(URI.create("/api/v1/credit-products/" + product.getId()))
                        .body(mapper.toProductDto(product)));
    }

    @Override
    public Mono<ResponseEntity<CreditProduct>> getCreditProductById(String productId, ServerWebExchange exchange) {
        return ReactiveBridge.toMono(service.get(productId))
                .map(product -> ResponseEntity.ok(mapper.toProductDto(product)));
    }

    @Override
    public Mono<ResponseEntity<Flux<CreditProduct>>> listCreditProducts(
            String customerId,
            CreditProductStatus status,
            ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(
                Flux.from(service.list(customerId, mapper.toModelProductStatus(status)))
                        .map(mapper::toProductDto)));
    }

    @Override
    public Mono<ResponseEntity<CreditProduct>> payCreditProduct(
            String productId,
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {
        return paymentRequest.flatMap(request -> ReactiveBridge.toMono(service.pay(
                        productId,
                        mapper.toBigDecimal(request.getAmount()))))
                .map(product -> ResponseEntity.ok(mapper.toProductDto(product)));
    }
}
