package br.com.danielcardoso.thindesk.ticket;

import java.time.Duration;
import java.time.Instant;

import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicalNote;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicianLevel;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;

/**
 * Montagem de chamados para os testes, passando pelas mesmas regras de domínio usadas pela API.
 */
public final class TicketFixtures {

	/** Segunda-feira, 09/11/2026 12:00 em São Paulo. */
	public static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");

	private static int sequence;

	private TicketFixtures() {
	}

	public static Technician technician(String id, String name) {
		Technician technician = new Technician(name, id + "@exemplo.com", TechnicianLevel.N1);
		technician.setId(id);
		return technician;
	}

	public static Ticket open(TicketType type, TicketPriority priority, Instant openedAt) {
		Ticket ticket = new Ticket("TD-2026-%06d".formatted(++sequence), "Chamado de teste", "Descrição", type,
				priority, "Maria", "maria@exemplo.com", Department.SALES, openedAt);
		ticket.setId("t" + sequence);
		return ticket;
	}

	public static Ticket inProgress(TicketType type, TicketPriority priority, Instant openedAt, Technician tech,
			Duration firstResponse) {
		Ticket ticket = open(type, priority, openedAt);
		ticket.assignTo(tech);
		ticket.changeStatus(TicketStatus.IN_PROGRESS, tech.getName(), openedAt.plus(firstResponse));
		return ticket;
	}

	public static Ticket resolved(TicketType type, TicketPriority priority, Instant openedAt, Technician tech,
			Duration firstResponse, Duration resolution) {
		Ticket ticket = inProgress(type, priority, openedAt, tech, firstResponse);
		Instant resolvedAt = openedAt.plus(resolution);
		ticket.addNote(new TechnicalNote(tech.getName(), "Solução", resolvedAt));
		ticket.changeStatus(TicketStatus.RESOLVED, tech.getName(), resolvedAt);
		return ticket;
	}

	public static Ticket closed(TicketType type, TicketPriority priority, Instant openedAt, Technician tech,
			Duration firstResponse, Duration resolution) {
		Ticket ticket = resolved(type, priority, openedAt, tech, firstResponse, resolution);
		ticket.changeStatus(TicketStatus.CLOSED, "Sistema", ticket.getResolvedAt().plus(Duration.ofDays(1)));
		return ticket;
	}

	public static Ticket canceled(TicketType type, Instant openedAt, Technician tech) {
		Ticket ticket = inProgress(type, TicketPriority.LOW, openedAt, tech, Duration.ofHours(1));
		ticket.changeStatus(TicketStatus.CANCELED, tech.getName(), openedAt.plus(Duration.ofHours(2)));
		return ticket;
	}
}
