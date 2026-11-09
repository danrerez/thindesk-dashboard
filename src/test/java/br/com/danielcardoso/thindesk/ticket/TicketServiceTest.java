package br.com.danielcardoso.thindesk.ticket;

import static br.com.danielcardoso.thindesk.ticket.TicketFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.dto.TicketRequests;
import br.com.danielcardoso.thindesk.ticket.repository.TechnicianRepository;
import br.com.danielcardoso.thindesk.ticket.repository.TicketRepository;
import br.com.danielcardoso.thindesk.ticket.service.TicketService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Serviço de chamados")
class TicketServiceTest {

	@Mock
	TicketRepository tickets;

	@Mock
	TechnicianRepository technicians;

	TicketService service;
	Technician ana = TicketFixtures.technician("ana", "Ana");

	@BeforeEach
	void setUp() {
		service = new TicketService(tickets, technicians, Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo")));
	}

	@Test
	@DisplayName("Abre chamado com protocolo sequencial, prazo de SLA e dados normalizados")
	void createsTicket() {
		when(tickets.nextProtocolSequence()).thenReturn(42L);
		when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
		when(technicians.findById("ana")).thenReturn(Optional.of(ana));

		Ticket created = service.create(new TicketRequests.CreateTicket("  Impressora parada  ", "Não imprime",
				TicketType.PRINTER, TicketPriority.HIGH, "João", " JOAO@Exemplo.COM ", Department.FINANCE, "ana"),
				"Gestor");

		assertThat(created.getProtocol()).isEqualTo("TD-2026-000042");
		assertThat(created.getTitle()).isEqualTo("Impressora parada");
		assertThat(created.getRequesterEmail()).isEqualTo("joao@exemplo.com");
		assertThat(created.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
		assertThat(created.getTechnicianName()).isEqualTo("Ana");
		assertThat(created.getNotes()).hasSize(1);
	}

	@Test
	@DisplayName("Resolver exige consideração técnica com a solução")
	void resolvingRequiresNote() {
		Ticket ticket = TicketFixtures.inProgress(TicketType.EMAIL, TicketPriority.LOW, NOW, ana, Duration.ZERO);
		when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));

		assertThatThrownBy(() -> service.changeStatus(ticket.getId(),
				new TicketRequests.ChangeStatus(TicketStatus.RESOLVED, "  "), "Gestor"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("solução");
		verify(tickets, never()).save(any());
	}

	@Test
	@DisplayName("Resolver com nota registra a solução e a data")
	void resolvesWithNote() {
		Ticket ticket = TicketFixtures.inProgress(TicketType.EMAIL, TicketPriority.LOW, NOW.minusSeconds(3600), ana,
				Duration.ZERO);
		when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

		Ticket saved = service.changeStatus(ticket.getId(),
				new TicketRequests.ChangeStatus(TicketStatus.RESOLVED, "Perfil recriado"), "Gestor");

		assertThat(saved.getStatus()).isEqualTo(TicketStatus.RESOLVED);
		assertThat(saved.getResolvedAt()).isEqualTo(NOW);
		assertThat(saved.getNotes()).last().satisfies(n -> {
			assertThat(n.text()).isEqualTo("Perfil recriado");
			assertThat(n.author()).isEqualTo("Gestor");
		});
	}

	@Test
	@DisplayName("Atribuição registra a transferência entre técnicos")
	void assignmentAddsNote() {
		Technician bruno = TicketFixtures.technician("bruno", "Bruno");
		Ticket ticket = TicketFixtures.inProgress(TicketType.ERP, TicketPriority.HIGH, NOW, ana, Duration.ZERO);
		when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(technicians.findById("bruno")).thenReturn(Optional.of(bruno));
		when(tickets.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

		Ticket saved = service.assign(ticket.getId(), "bruno", "Gestor");

		assertThat(saved.getTechnicianId()).isEqualTo("bruno");
		assertThat(saved.getNotes()).last().extracting(n -> n.text())
				.isEqualTo("Chamado transferido de Ana para Bruno.");
	}

	@Test
	@DisplayName("Técnico inexistente e chamado inexistente geram erros distintos")
	void errors() {
		Ticket ticket = TicketFixtures.open(TicketType.ERP, TicketPriority.HIGH, NOW);
		when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
		when(technicians.findById("x")).thenReturn(Optional.empty());
		when(tickets.findById("nao-existe")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.assign(ticket.getId(), "x", "Gestor"))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service.get("nao-existe")).isInstanceOf(ResourceNotFoundException.class);
	}
}
