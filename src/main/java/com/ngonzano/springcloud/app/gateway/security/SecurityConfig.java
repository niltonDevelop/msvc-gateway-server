package com.ngonzano.springcloud.app.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
public class SecurityConfig {

	/**
	 * Rutas públicas sin validación JWT: si el cliente envía Bearer inválido/expirado
	 * no debe bloquearse (comportamiento típico de Spring Resource Server en una sola cadena).
	 */
	@Bean
	@Order(1)
	SecurityWebFilterChain publicApiSecurityFilterChain(ServerHttpSecurity http) {
		return http
				.securityMatcher(ServerWebExchangeMatchers.matchers(
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/auth/login"),
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/api/auth/**"),
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/users/v1"),
						ServerWebExchangeMatchers.pathMatchers(
								HttpMethod.GET,
								"/api/items/**",
								"/api/users/**",
								"/api/products/**")))
				.authorizeExchange(authz -> authz.anyExchange().permitAll())
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.build();
	}

	@Bean
	@Order(2)
	SecurityWebFilterChain securityWebFilterChain(
			ServerHttpSecurity http,
			ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter) {
		return http.authorizeExchange(authz -> authz
				.pathMatchers("/logout", "/login/oauth2/code/**", "/oauth2/authorization/**").permitAll()
				.pathMatchers(HttpMethod.GET, "/api/items/{id}", "/api/products/{id}", "/api/users/{id}")
				.hasAnyRole("ADMIN", "USER")
				.pathMatchers("/api/items/**", "/api/products/**", "/api/users/**").hasRole("ADMIN")
				.anyExchange().authenticated())
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.oauth2Login(Customizer.withDefaults())
				.oauth2Client(Customizer.withDefaults())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
						.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.build();
	}
}
