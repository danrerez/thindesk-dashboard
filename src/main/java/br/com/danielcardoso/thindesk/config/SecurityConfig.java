package br.com.danielcardoso.thindesk.config;

import java.util.Map;

import org.mongodb.spring.session.MongoSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

import br.com.danielcardoso.thindesk.security.ApiSecurityHandlers;
import br.com.danielcardoso.thindesk.security.LoginFailureHandler;
import br.com.danielcardoso.thindesk.security.LoginSuccessHandler;
import br.com.danielcardoso.thindesk.user.domain.Role;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	static final int BCRYPT_STRENGTH = 12;

	static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
			+ "img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self'; "
			+ "form-action 'self'; frame-ancestors 'none'";

	/** O Swagger UI usa estilos inline; a exceção vale só para as páginas dele. */
	static final String SWAGGER_CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
			+ "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; object-src 'none'; "
			+ "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	static final RequestMatcher SWAGGER_PAGES = request -> request.getRequestURI()
			.substring(request.getContextPath().length()).startsWith("/swagger-ui");

	/** Sempre volta para o login; sem isso o HTTP Basic faria o logout responder 204 para clientes sem HTML. */
	private static SimpleUrlLogoutSuccessHandler logoutSuccessHandler() {
		SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
		handler.setDefaultTargetUrl("/login?logout");
		return handler;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(BCRYPT_STRENGTH)));
	}

	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
				.role(Role.ADMIN.name()).implies(Role.MANAGER.name())
				.role(Role.MANAGER.name()).implies(Role.USER.name())
				.build();
	}

	@Bean
	SpringSessionBackedSessionRegistry<MongoSession> sessionRegistry(
			FindByIndexNameSessionRepository<MongoSession> sessionRepository) {
		return new SpringSessionBackedSessionRegistry<>(sessionRepository);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler successHandler,
			LoginFailureHandler failureHandler, SpringSessionBackedSessionRegistry<MongoSession> sessionRegistry,
			AppProperties properties) throws Exception {
		http
				.authorizeHttpRequests(AuthorizationRules::apply)
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.usernameParameter(LoginFailureHandler.USERNAME_PARAMETER)
						.passwordParameter("password")
						.successHandler(successHandler)
						.failureHandler(failureHandler)
						.permitAll())
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler(logoutSuccessHandler())
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(MongoSessionConfig.COOKIE_NAME)
						.permitAll())
				.httpBasic(basic -> basic.authenticationEntryPoint(ApiSecurityHandlers.unauthorized()))
				.csrf(csrf -> csrf.ignoringRequestMatchers(ApiSecurityHandlers.API_WITH_BASIC_AUTH))
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(ApiSecurityHandlers.unauthorized(), ApiSecurityHandlers.API)
						.defaultAccessDeniedHandlerFor(ApiSecurityHandlers.forbidden(), ApiSecurityHandlers.API)
						// Páginas sempre vão para o login, mesmo sem "Accept: text/html" (o HTTP Basic é só da API).
						.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
								new NegatedRequestMatcher(ApiSecurityHandlers.API)))
				.sessionManagement(session -> session
						.sessionFixation(fixation -> fixation.changeSessionId())
						.sessionConcurrency(concurrency -> concurrency
								.maximumSessions(properties.security().maxSessionsPerUser())
								.sessionRegistry(sessionRegistry)
								.expiredUrl("/login?expired")))
				.headers(headers -> headers
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(SWAGGER_PAGES,
								new StaticHeadersWriter("Content-Security-Policy", SWAGGER_CONTENT_SECURITY_POLICY)))
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(SWAGGER_PAGES),
								new StaticHeadersWriter("Content-Security-Policy", CONTENT_SECURITY_POLICY)))
						.frameOptions(frame -> frame.deny())
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
		return http.build();
	}
}
