package com.ngonzano.springcloud.app.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        return http.authorizeExchange(authz -> authz
                .pathMatchers("/logout", "/login/oauth2/code/**", "/oauth2/authorization/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/users/v1").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/items/**", "/api/users/**", "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/items/{id}", "/api/products/{id}", "/api/users/{id}")
                .hasAnyRole("ADMIN", "USER")
                .pathMatchers("/api/items/**", "/api/products/**", "/api/users/**").hasRole("ADMIN")
                .anyExchange().authenticated())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .oauth2Login(Customizer.withDefaults())
                .oauth2Client(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
