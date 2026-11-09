package br.com.danielcardoso.thindesk.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import br.com.danielcardoso.thindesk.user.domain.Role;

public final class AuthorizationRules {

	public static final String[] PUBLIC_PATHS = {
			"/", "/login", "/register", "/error", "/error/**",
			"/css/**", "/js/**", "/images/**", "/themes/**", "/webjars/**", "/favicon.ico"
	};

	/** Documentação da API (OpenAPI/Swagger). Pública: descreve os endpoints, mas não expõe dados. */
	public static final String[] API_DOCS_PATHS = {
			"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
	};

	/** Área do responsável pelos chamados: dashboard, telas de chamados e toda a API REST. */
	public static final String[] TICKET_AREA_PATHS = {
			"/dashboard", "/dashboard/**", "/tickets", "/tickets/**", "/api/**"
	};

	private AuthorizationRules() {
	}

	public static void apply(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
		auth
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.requestMatchers(API_DOCS_PATHS).permitAll()
				.requestMatchers(TICKET_AREA_PATHS).hasRole(Role.MANAGER.name())
				.requestMatchers(HttpMethod.POST, "/users/**").hasRole(Role.ADMIN.name())
				.requestMatchers("/users/**").hasRole(Role.MANAGER.name())
				.anyRequest().authenticated();
	}
}
