package br.com.danielcardoso.thindesk.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record AppProperties(
		@DefaultValue Theme theme,
		@DefaultValue Security security,
		@DefaultValue Admin admin,
		@DefaultValue Dashboard dashboard,
		@DefaultValue Seed seed) {

	public record Theme(
			@DefaultValue("default") String name,
			@DefaultValue("layouts/main") String layout) {
	}

	public record Security(
			@DefaultValue("5") int maxLoginAttempts,
			@DefaultValue("15m") Duration lockDuration,
			@DefaultValue("3") int maxSessionsPerUser,
			@DefaultValue("30m") Duration sessionTimeout,
			@DefaultValue("false") boolean secureCookie) {
	}

	public record Admin(
			@DefaultValue("Administrador") String name,
			String email,
			String password) {
	}

	/**
	 * @param zone fuso usado para interpretar as datas dos filtros e agrupar a série histórica
	 * @param refreshSeconds intervalo da atualização automática do painel
	 */
	public record Dashboard(
			@DefaultValue("America/Sao_Paulo") ZoneId zone,
			@DefaultValue("60") int refreshSeconds) {
	}

	/**
	 * Massa de dados de demonstração, gerada só quando a coleção de chamados está vazia (ou sempre, com
	 * {@code reset=true}, que apaga os chamados e técnicos antes de gerar).
	 */
	public record Seed(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("false") boolean reset,
			@DefaultValue("1400") int tickets,
			@DefaultValue("180") int days) {
	}
}
