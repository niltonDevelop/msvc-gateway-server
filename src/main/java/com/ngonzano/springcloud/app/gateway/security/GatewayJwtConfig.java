package com.ngonzano.springcloud.app.gateway.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import reactor.core.publisher.Mono;

@Configuration
public class GatewayJwtConfig {

	@Bean
	ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
		return new ReactiveJwtAuthenticationConverterAdapter(converter);
	}

	private Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
		return jwt -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>();

			String scope = jwt.getClaimAsString("scope");
			if (scope != null && !scope.isBlank()) {
				for (String value : scope.split(" ")) {
					if (!value.isBlank()) {
						authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
					}
				}
			}

			List<String> roles = jwt.getClaimAsStringList("roles");
			if (roles != null) {
				for (String role : roles) {
					if (role == null || role.isBlank()) {
						continue;
					}
					authorities.add(new SimpleGrantedAuthority(
							role.startsWith("ROLE_") ? role : "ROLE_" + role));
				}
			}

			return authorities;
		};
	}
}
