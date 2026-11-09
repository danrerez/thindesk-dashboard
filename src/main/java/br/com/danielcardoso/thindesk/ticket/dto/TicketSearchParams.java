package br.com.danielcardoso.thindesk.ticket.dto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.service.TicketFilter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Filtros de consulta (RF05) comuns ao dashboard e à listagem de chamados. Listas aceitam valores repetidos
 * ({@code status=OPEN&status=IN_PROGRESS}) ou separados por vírgula ({@code status=OPEN,IN_PROGRESS}).
 *
 * @param from data inicial de abertura (inclusiva), formato {@code yyyy-MM-dd}
 * @param to data final de abertura (inclusiva), formato {@code yyyy-MM-dd}
 * @param technicianId id do técnico ou {@code none} para chamados sem técnico
 * @param q busca por protocolo, título, solicitante ou técnico
 */
public record TicketSearchParams(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		List<TicketStatus> status,
		List<TicketType> type,
		List<TicketPriority> priority,
		List<Department> department,
		@Size(max = 40, message = "technicianId inválido") String technicianId,
		@Size(max = 80, message = "A busca pode ter até 80 caracteres") String q,
		Boolean overdue) {

	public static final int MAX_PERIOD_DAYS = 731;

	@AssertTrue(message = "A data inicial (from) deve ser anterior ou igual à data final (to)")
	public boolean isPeriodOrdered() {
		return from == null || to == null || !from.isAfter(to);
	}

	@AssertTrue(message = "O período pode ter no máximo " + MAX_PERIOD_DAYS + " dias")
	public boolean isPeriodWithinLimit() {
		return from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) < MAX_PERIOD_DAYS;
	}

	public TicketFilter toFilter(ZoneId zone) {
		return new TicketFilter(
				from == null ? null : from.atStartOfDay(zone).toInstant(),
				to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
				setOf(status),
				setOf(type),
				setOf(priority),
				setOf(department),
				StringUtils.hasText(technicianId) ? technicianId.trim() : null,
				StringUtils.hasText(q) ? q.trim() : null,
				Boolean.TRUE.equals(overdue));
	}

	private static <T> Set<T> setOf(List<T> values) {
		return values == null ? Set.of() : values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
	}

	public static TicketSearchParams none() {
		return new TicketSearchParams(null, null, null, null, null, null, null, null, null);
	}
}
