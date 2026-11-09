package br.com.danielcardoso.thindesk.ticket;

import static br.com.danielcardoso.thindesk.ticket.TicketFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicalNote;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;

@DisplayName("Fluxo de situações do chamado")
class TicketWorkflowTest {

	private final Technician tech = TicketFixtures.technician("tec1", "Ana");

	@Test
	@DisplayName("Prazo de SLA é calculado pela prioridade na abertura")
	void dueDateFollowsPrioritySla() {
		Ticket critical = TicketFixtures.open(TicketType.NETWORK, TicketPriority.CRITICAL, NOW);
		Ticket low = TicketFixtures.open(TicketType.NETWORK, TicketPriority.LOW, NOW);

		assertThat(critical.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
		assertThat(low.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
		assertThat(critical.getStatus()).isEqualTo(TicketStatus.OPEN);
	}

	@Test
	@DisplayName("Não inicia atendimento sem técnico atribuído")
	void cannotStartWithoutTechnician() {
		Ticket ticket = TicketFixtures.open(TicketType.HARDWARE, TicketPriority.HIGH, NOW);

		assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.IN_PROGRESS, "Ana", NOW))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Atribua um técnico");
	}

	@Test
	@DisplayName("Registra primeira resposta, solução e fechamento com histórico")
	void recordsTimestampsAndHistory() {
		Ticket ticket = TicketFixtures.closed(TicketType.HARDWARE, TicketPriority.HIGH, NOW, tech,
				Duration.ofMinutes(30), Duration.ofHours(5));

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
		assertThat(ticket.firstResponseTime()).isEqualTo(Duration.ofMinutes(30));
		assertThat(ticket.resolutionTime()).isEqualTo(Duration.ofHours(5));
		assertThat(ticket.getClosedAt()).isEqualTo(NOW.plus(Duration.ofHours(29)));
		assertThat(ticket.slaMet()).isTrue();
		assertThat(ticket.getHistory()).extracting(h -> h.to())
				.containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED);
	}

	@Test
	@DisplayName("Transições fora do fluxo são recusadas")
	void rejectsInvalidTransitions() {
		Ticket open = TicketFixtures.open(TicketType.EMAIL, TicketPriority.LOW, NOW);
		assertThatThrownBy(() -> open.changeStatus(TicketStatus.RESOLVED, "Ana", NOW))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Aberto para Resolvido");

		Ticket closed = TicketFixtures.closed(TicketType.EMAIL, TicketPriority.LOW, NOW, tech, Duration.ofHours(1),
				Duration.ofHours(2));
		assertThat(closed.getStatus().nextStatuses()).isEmpty();
		assertThatThrownBy(() -> closed.changeStatus(TicketStatus.IN_PROGRESS, "Ana", NOW))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> closed.addNote(new TechnicalNote("Ana", "x", NOW)))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> closed.assignTo(tech)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("Mesma situação não gera mudança")
	void rejectsSameStatus() {
		Ticket ticket = TicketFixtures.inProgress(TicketType.EMAIL, TicketPriority.LOW, NOW, tech, Duration.ZERO);
		assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.IN_PROGRESS, "Ana", NOW))
				.hasMessageContaining("já está");
	}

	@Test
	@DisplayName("Reabrir um chamado resolvido limpa a data de solução e mantém a primeira resposta")
	void reopeningClearsResolution() {
		Ticket ticket = TicketFixtures.resolved(TicketType.ERP, TicketPriority.MEDIUM, NOW, tech, Duration.ofHours(1),
				Duration.ofHours(3));

		ticket.changeStatus(TicketStatus.IN_PROGRESS, "Ana", NOW.plus(Duration.ofHours(4)));

		assertThat(ticket.getResolvedAt()).isNull();
		assertThat(ticket.firstResponseTime()).isEqualTo(Duration.ofHours(1));
		assertThat(ticket.getStatus().isActive()).isTrue();
	}

	@Test
	@DisplayName("Atraso: em aberto após o prazo ou resolvido depois dele")
	void overdueRules() {
		Ticket late = TicketFixtures.inProgress(TicketType.ERP, TicketPriority.CRITICAL, NOW, tech, Duration.ZERO);
		assertThat(late.isOverdue(NOW.plus(Duration.ofHours(3)))).isFalse();
		assertThat(late.isOverdue(NOW.plus(Duration.ofHours(5)))).isTrue();

		Ticket resolvedLate = TicketFixtures.resolved(TicketType.ERP, TicketPriority.CRITICAL, NOW, tech,
				Duration.ZERO, Duration.ofHours(6));
		assertThat(resolvedLate.slaMet()).isFalse();
		assertThat(TicketFixtures.open(TicketType.ERP, TicketPriority.LOW, NOW).slaMet()).isNull();
	}

	@Test
	@DisplayName("Técnico inativo não recebe chamados")
	void inactiveTechnicianCannotBeAssigned() {
		Technician former = TicketFixtures.technician("tec9", "Eduardo");
		former.setActive(false);
		Ticket ticket = TicketFixtures.open(TicketType.PHONE, TicketPriority.LOW, NOW);

		assertThatThrownBy(() -> ticket.assignTo(former)).hasMessageContaining("inativo");
	}
}
