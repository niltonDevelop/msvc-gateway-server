package com.ngonzano.springcloud.app.gateway.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/products")
    public Mono<ResponseEntity<Map<String, String>>> productsFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Servicio products no disponible temporalmente",
                        "service", "msvc-products")));
    }

    @RequestMapping("/fallback/items")
    public Mono<ResponseEntity<Map<String, String>>> itemsFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Servicio items no disponible temporalmente",
                        "service", "msvc-items")));
    }
}
