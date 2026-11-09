package br.com.danielcardoso.thindesk.ticket.dto;

import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpos de requisição aceitos pela API de chamados.
 */
public final class TicketRequests {

	private TicketRequests() {
	}

	public record CreateTicket(
			@NotBlank(message = "Informe o título") @Size(min = 5, max = 120, message = "O título deve ter entre 5 e 120 caracteres") String title,
			@NotBlank(message = "Descreva o problema") @Size(max = 4000, message = "A descrição pode ter até 4000 caracteres") String description,
			@NotNull(message = "Informe o tipo do chamado") TicketType type,
			@NotNull(message = "Informe a prioridade") TicketPriority priority,
			@NotBlank(message = "Informe o nome do solicitante") @Size(max = 80, message = "O nome pode ter até 80 caracteres") String requesterName,
			@NotBlank(message = "Informe o e-mail do solicitante") @Email(message = "E-mail inválido") @Size(max = 120) String requesterEmail,
			@NotNull(message = "Informe o setor do solicitante") Department department,
			String technicianId) {
	}

	public record ChangeStatus(
			@NotNull(message = "Informe a nova situação") TicketStatus status,
			@Size(max = 2000, message = "A consideração técnica pode ter até 2000 caracteres") String note) {
	}

	public record Assign(
			@NotBlank(message = "Informe o técnico") String technicianId) {
	}

	public record AddNote(
			@NotBlank(message = "Escreva a consideração técnica") @Size(max = 2000, message = "A consideração técnica pode ter até 2000 caracteres") String text) {
	}
}
