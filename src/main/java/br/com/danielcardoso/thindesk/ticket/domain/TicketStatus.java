package br.com.danielcardoso.thindesk.ticket.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um chamado. As transições permitidas ficam centralizadas aqui para que a API, a tela e os
 * testes usem a mesma regra.
 */
public enum TicketStatus {

	OPEN("Aberto"),
	IN_PROGRESS("Em atendimento"),
	WAITING_USER("Aguardando usuário"),
	RESOLVED("Resolvido"),
	CLOSED("Fechado"),
	CANCELED("Cancelado");

	/** Chamados que ainda exigem trabalho da equipe (backlog). */
	public static final Set<TicketStatus> ACTIVE = EnumSet.of(OPEN, IN_PROGRESS, WAITING_USER);

	/** Chamados atendidos, ou seja, com solução registrada. */
	public static final Set<TicketStatus> SOLVED = EnumSet.of(RESOLVED, CLOSED);

	private final String label;

	TicketStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean isActive() {
		return ACTIVE.contains(this);
	}

	public boolean isSolved() {
		return SOLVED.contains(this);
	}

	public boolean isFinal() {
		return this == CLOSED || this == CANCELED;
	}

	public Set<TicketStatus> nextStatuses() {
		return switch (this) {
			case OPEN -> EnumSet.of(IN_PROGRESS, CANCELED);
			case IN_PROGRESS -> EnumSet.of(WAITING_USER, RESOLVED, CANCELED);
			case WAITING_USER -> EnumSet.of(IN_PROGRESS, RESOLVED, CANCELED);
			case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);
			case CLOSED, CANCELED -> EnumSet.noneOf(TicketStatus.class);
		};
	}

	public boolean canMoveTo(TicketStatus target) {
		return nextStatuses().contains(target);
	}
}
