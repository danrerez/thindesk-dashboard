package br.com.danielcardoso.thindesk.ticket.service;

import java.time.Instant;
import java.util.Set;

import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;

/**
 * Critérios de consulta já normalizados (datas em {@link Instant}, coleções nunca nulas). O intervalo de datas é
 * aplicado sobre a data de abertura: {@code from} inclusivo e {@code to} exclusivo.
 *
 * @param technicianId id do técnico, ou {@link #UNASSIGNED} para chamados sem técnico
 */
public record TicketFilter(
		Instant from,
		Instant to,
		Set<TicketStatus> statuses,
		Set<TicketType> types,
		Set<TicketPriority> priorities,
		Set<Department> departments,
		String technicianId,
		String text,
		boolean overdueOnly) {

	public static final String UNASSIGNED = "none";

	public TicketFilter {
		statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
		types = types == null ? Set.of() : Set.copyOf(types);
		priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
		departments = departments == null ? Set.of() : Set.copyOf(departments);
	}

	public static TicketFilter empty() {
		return new TicketFilter(null, null, null, null, null, null, null, null, false);
	}

	public static TicketFilter between(Instant from, Instant to) {
		return new TicketFilter(from, to, null, null, null, null, null, null, false);
	}

	/** Mesmos critérios em outro intervalo (usado na comparação com o período anterior). */
	public TicketFilter withPeriod(Instant newFrom, Instant newTo) {
		return new TicketFilter(newFrom, newTo, statuses, types, priorities, departments, technicianId, text,
				overdueOnly);
	}

	public boolean hasPeriod() {
		return from != null && to != null;
	}
}
