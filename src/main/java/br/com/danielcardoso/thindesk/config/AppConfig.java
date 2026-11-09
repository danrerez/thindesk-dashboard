package br.com.danielcardoso.thindesk.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

	/** Relógio injetável: permite testar regras de SLA e séries históricas com uma data fixa. */
	@Bean
	Clock clock(AppProperties properties) {
		return Clock.system(properties.dashboard().zone());
	}
}
