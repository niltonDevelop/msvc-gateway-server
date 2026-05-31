package com.ngonzano.springcloud.app.gateway.filters;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class SampleGlobalFilter implements GlobalFilter, Ordered {

    private final Logger logger = LoggerFactory.getLogger(SampleGlobalFilter.class);
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("Ejecutando filtro pre");

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.add("token", "1234567890"))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            logger.info("Ejecutando filtro post");
            String token = mutatedExchange.getRequest().getHeaders().getFirst("token");
            logger.info("token: {}", token);
            if (token != null) {
                mutatedExchange.getResponse().getHeaders().add("token", token);
            }

            Optional.ofNullable(mutatedExchange.getRequest().getHeaders().getFirst("token")).ifPresent(value -> {
                logger.info("token2: {}", value);
                mutatedExchange.getResponse().getHeaders().add("token2", value);
            });

            mutatedExchange.getResponse().getCookies().add("color", ResponseCookie.from("color", "red").build());
            //mutatedExchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        }));         
    }
    @Override
    public int getOrder() {
        return 100;
    }
}
