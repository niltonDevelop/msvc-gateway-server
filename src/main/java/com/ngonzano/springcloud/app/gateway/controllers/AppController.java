package com.ngonzano.springcloud.app.gateway.controllers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class AppController {

    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

    public AppController(ReactiveOAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/")
    public Mono<Map<String, Object>> home(Authentication authentication) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("authenticated", authentication != null && authentication.isAuthenticated());
        if (authentication != null) {
            payload.put("name", authentication.getName());
            payload.put("authorities", authentication.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        }
        return Mono.just(payload);
    }

    @GetMapping("/tokens")
    public Mono<Map<String, Object>> tokens(OAuth2AuthenticationToken authentication) {
        return authorizedClientService.loadAuthorizedClient(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getName())
                .map(this::toTokenPayload)
                .switchIfEmpty(Mono.just(Collections.singletonMap("error", "No hay tokens OAuth2 en sesion")));
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Collections.singletonMap("message", "Logout successful");
    }

    private Map<String, Object> toTokenPayload(OAuth2AuthorizedClient client) {
        Map<String, Object> tokens = new HashMap<>();
        tokens.put("access_token", client.getAccessToken().getTokenValue());
        tokens.put("token_type", client.getAccessToken().getTokenType().getValue());
        tokens.put("expires_at", client.getAccessToken().getExpiresAt());
        if (client.getAccessToken().getScopes() != null) {
            tokens.put("scope", String.join(" ", client.getAccessToken().getScopes()));
        }
        if (client.getRefreshToken() != null) {
            tokens.put("refresh_token", client.getRefreshToken().getTokenValue());
        }
        return tokens;
    }
}
