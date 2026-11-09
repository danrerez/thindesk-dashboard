package br.com.danielcardoso.thindesk.ticket.domain;

import java.time.Duration;

/**
 * Prioridade do chamado e o prazo de solução (SLA) acordado para cada uma.
 */
public enum TicketPriority {

	LOW("Baixa", 72),
	MEDIUM("Média", 24),
	HIGH("Alta", 8),
	CRITICAL("Crítica", 4);

	private final String label;
	private final int slaHours;

	TicketPriority(String label, int slaHours) {
		this.label = label;
		this.slaHours = slaHours;
	}

	public String getLabel() {
		return label;
	}

	public int getSlaHours() {
		return slaHours;
	}

	public Duration sla() {
		return Duration.ofHours(slaHours);
	}
}
