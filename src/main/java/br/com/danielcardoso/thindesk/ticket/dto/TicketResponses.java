package br.com.danielcardoso.thindesk.ticket.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.StatusChange;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicalNote;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;

/**
 * Representações devolvidas pela API. Enums vão como objeto {@code {code, label}} para que o front não precise
 * manter uma tabela de tradução própria.
 */
public final class TicketResponses {

	private TicketResponses() {
	}

	public record Option(String code, String label) {

		public static Option of(TicketStatus status) {
			return status == null ? null : new Option(status.name(), status.getLabel());
		}

		public static Option of(TicketType type) {
			return type == null ? null : new Option(type.name(), type.getLabel());
		}

		public static Option of(TicketPriority priority) {
			return priority == null ? null : new Option(priority.name(), priority.getLabel());
		}

		public static Option of(Department department) {
			return department == null ? null : new Option(department.name(), department.getLabel());
		}
	}

	public record TechnicianRef(String id, String name) {
	}

	public record TicketSummary(
			String id,
			String protocol,
			String title,
			Option type,
			Option priority,
			Option status,
			Option department,
			String requesterName,
			TechnicianRef technician,
			Instant openedAt,
			Instant dueAt,
			Instant resolvedAt,
			boolean overdue) {

		public static TicketSummary from(Ticket t, Instant now) {
			return new TicketSummary(t.getId(), t.getProtocol(), t.getTitle(), Option.of(t.getType()),
					Option.of(t.getPriority()), Option.of(t.getStatus()), Option.of(t.getDepartment()),
					t.getRequesterName(), technicianOf(t), t.getOpenedAt(), t.getDueAt(), t.getResolvedAt(),
					t.isOverdue(now));
		}
	}

	public record NoteResponse(String author, String text, Instant createdAt) {

		static NoteResponse from(TechnicalNote note) {
			return new NoteResponse(note.author(), note.text(), note.createdAt());
		}
	}

	public record HistoryResponse(Option from, Option to, String changedBy, Instant changedAt) {

		static HistoryResponse from(StatusChange change) {
			return new HistoryResponse(Option.of(change.from()), Option.of(change.to()), change.changedBy(),
					change.changedAt());
		}
	}

	public record TicketDetail(
			String id,
			String protocol,
			String title,
			String description,
			Option type,
			Option priority,
			Option status,
			Option department,
			String requesterName,
			String requesterEmail,
			TechnicianRef technician,
			Instant openedAt,
			Instant dueAt,
			Instant firstResponseAt,
			Instant resolvedAt,
			Instant closedAt,
			boolean overdue,
			Boolean slaMet,
			int slaHours,
			List<Option> allowedTransitions,
			List<NoteResponse> notes,
			List<HistoryResponse> history) {

		public static TicketDetail from(Ticket t, Instant now) {
			List<Option> transitions = t.getStatus().nextStatuses().stream()
					.sorted(Comparator.comparingInt(Enum::ordinal))
					.map(Option::of)
					.toList();
			return new TicketDetail(t.getId(), t.getProtocol(), t.getTitle(), t.getDescription(),
					Option.of(t.getType()), Option.of(t.getPriority()), Option.of(t.getStatus()),
					Option.of(t.getDepartment()), t.getRequesterName(), t.getRequesterEmail(), technicianOf(t),
					t.getOpenedAt(), t.getDueAt(), t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(),
					t.isOverdue(now), t.slaMet(), t.getPriority().getSlaHours(), transitions,
					t.getNotes().stream().map(NoteResponse::from).toList(),
					t.getHistory().stream().map(HistoryResponse::from).toList());
		}
	}

	public record TechnicianResponse(String id, String name, String email, Option level, boolean active) {

		public static TechnicianResponse from(Technician t) {
			return new TechnicianResponse(t.getId(), t.getName(), t.getEmail(),
					new Option(t.getLevel().name(), t.getLevel().getLabel()), t.isActive());
		}
	}

	public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

		public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
			return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
					page.getSize(), page.getTotalElements(), page.getTotalPages());
		}
	}

	private static TechnicianRef technicianOf(Ticket t) {
		return t.getTechnicianId() == null ? null : new TechnicianRef(t.getTechnicianId(), t.getTechnicianName());
	}
}
