package br.com.danielcardoso.thindesk.config;

import java.util.EnumSet;

import org.mongodb.spring.session.MongoIndexedSessionRepository;
import org.mongodb.spring.session.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableMongoHttpSession(collectionName = MongoSessionConfig.COLLECTION)
public class MongoSessionConfig {

	public static final String COLLECTION = "sessions";
	public static final String COOKIE_NAME = "SESSION";

	@Bean
	SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionTimeoutCustomizer(AppProperties properties) {
		return repository -> repository.setDefaultMaxInactiveInterval(properties.security().sessionTimeout());
	}

	@Bean
	FilterRegistrationBean<SessionRepositoryFilter<?>> sessionRepositoryFilterRegistration(
			SessionRepositoryFilter<?> filter) {
		FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(filter);
		registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
		registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
		return registration;
	}

	@Bean
	CookieSerializer cookieSerializer(AppProperties properties) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(properties.security().secureCookie());
		return serializer;
	}
}
