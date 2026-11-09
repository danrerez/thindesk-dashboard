package br.com.danielcardoso.thindesk.dashboard;

import static br.com.danielcardoso.thindesk.ticket.TicketFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.danielcardoso.thindesk.common.exception.InvalidParameterException;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.StatusDistribution;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianRanking;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Timeline;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TimelinePoint;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Totals;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TypeRanking;
import br.com.danielcardoso.thindesk.ticket.TicketFixtures;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;

/**
 * Cenário fixo com 7 chamados entre 02/11 e 09/11/2026, com os resultados esperados calculados à mão.
 */
@DisplayName("Cálculo dos indicadores do dashboard")
class DashboardMetricsTest {

	static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

	Technician ana = TicketFixtures.technician("ana", "Ana");
	Technician bruno = TicketFixtures.technician("bruno", "Bruno");
	List<Ticket> tickets;

	@BeforeEach
	void scenario() {
		tickets = List.of(
				// t1: hardware alta, resolvido em 5h (SLA 8h) e fechado
				TicketFixtures.closed(TicketType.HARDWARE, TicketPriority.HIGH, at("2026-11-02T10:00:00Z"), ana,
						Duration.ofHours(1), Duration.ofHours(5)),
				// t2: hardware crítica, resolvido em 6h (SLA 4h, estourou)
				TicketFixtures.resolved(TicketType.HARDWARE, TicketPriority.CRITICAL, at("2026-11-03T13:00:00Z"), ana,
						Duration.ofMinutes(30), Duration.ofHours(6)),
				// t3: rede média, resolvido em 10h e fechado
				TicketFixtures.closed(TicketType.NETWORK, TicketPriority.MEDIUM, at("2026-11-04T13:00:00Z"), bruno,
						Duration.ofHours(2), Duration.ofHours(10)),
				// t4: acesso baixa, em atendimento e já fora do prazo de 72h
				TicketFixtures.inProgress(TicketType.ACCESS, TicketPriority.LOW, at("2026-11-05T13:00:00Z"), bruno,
						Duration.ofHours(4)),
				// t5: e-mail média, aberto hoje sem técnico
				TicketFixtures.open(TicketType.EMAIL, TicketPriority.MEDIUM, at("2026-11-09T12:00:00Z")),
				// t6: rede cancelado
				TicketFixtures.canceled(TicketType.NETWORK, at("2026-11-06T13:00:00Z"), bruno),
				// t7: ERP crítico, aberto hoje sem técnico, ainda no prazo
				TicketFixtures.open(TicketType.ERP, TicketPriority.CRITICAL, at("2026-11-09T14:00:00Z")));
	}

	@Test
	@DisplayName("RF01: totais por situação, atrasados, críticos e sem técnico")
	void totals() {
		Totals totals = DashboardMetrics.totals(tickets, NOW);

		assertThat(totals.total()).isEqualTo(7);
		assertThat(totals.active()).isEqualTo(3);
		assertThat(totals.open()).isEqualTo(2);
		assertThat(totals.inProgress()).isEqualTo(1);
		assertThat(totals.waitingUser()).isZero();
		assertThat(totals.solved()).isEqualTo(3);
		assertThat(totals.canceled()).isEqualTo(1);
		assertThat(totals.overdue()).isEqualTo(1);
		assertThat(totals.criticalActive()).isEqualTo(1);
		assertThat(totals.unassignedActive()).isEqualTo(2);
	}

	@Test
	@DisplayName("RF01: taxa de resolução ignora cancelados e SLA considera só resolvidos")
	void rates() {
		var rates = DashboardMetrics.rates(tickets);

		assertThat(rates.resolutionRate()).isEqualTo(50.0);
		assertThat(rates.slaMetPercent()).isEqualTo(66.7);
	}

	@Test
	@DisplayName("RF01: tempos médios e mediana em horas")
	void times() {
		var times = DashboardMetrics.times(tickets);

		assertThat(times.avgFirstResponseHours()).isEqualTo(1.7);
		assertThat(times.avgResolutionHours()).isEqualTo(7.0);
		assertThat(times.medianResolutionHours()).isEqualTo(6.0);
	}

	@Test
	@DisplayName("Sem dados: zeros nos totais e null (não 0) nas médias e percentuais")
	void emptyDataUsesNullAverages() {
		assertThat(DashboardMetrics.totals(List.of(), NOW).total()).isZero();
		assertThat(DashboardMetrics.rates(List.of()).slaMetPercent()).isNull();
		assertThat(DashboardMetrics.times(List.of()).avgResolutionHours()).isNull();
		assertThat(DashboardMetrics.timeline(List.of(), null, null, null, SP).points()).isEmpty();
	}

	@Test
	@DisplayName("Comparação com período anterior")
	void change() {
		assertThat(DashboardMetrics.change(200, 150)).isEqualTo(-25.0);
		assertThat(DashboardMetrics.change(40, 50)).isEqualTo(25.0);
		assertThat(DashboardMetrics.change(0, 5)).isNull();
	}

	@Test
	@DisplayName("RF02: série diária com dias sem movimento preenchidos com zero")
	void dailyTimeline() {
		Timeline timeline = DashboardMetrics.timeline(tickets, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9),
				null, SP);

		assertThat(timeline.granularity().code()).isEqualTo("DAY");
		assertThat(timeline.points()).hasSize(8);
		assertThat(timeline.points()).extracting(TimelinePoint::opened).containsExactly(1L, 1L, 1L, 1L, 1L, 0L, 0L, 2L);
		assertThat(timeline.points()).extracting(TimelinePoint::solved).containsExactly(1L, 1L, 1L, 0L, 0L, 0L, 0L, 0L);
		assertThat(timeline.points().get(1).avgResolutionHours()).isEqualTo(6.0);
		assertThat(timeline.points().get(5).avgResolutionHours()).isNull();
		assertThat(timeline.points().get(0).label()).isEqualTo("02/11");
		assertThat(timeline.totalOpened()).isEqualTo(7);
		assertThat(timeline.totalSolved()).isEqualTo(3);
	}

	@Test
	@DisplayName("RF02: agrupamento semanal começa na segunda-feira")
	void weeklyTimeline() {
		Timeline timeline = DashboardMetrics.timeline(tickets, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9),
				Granularity.WEEK, SP);

		assertThat(timeline.points()).extracting(TimelinePoint::period)
				.containsExactly(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));
		assertThat(timeline.points()).extracting(TimelinePoint::opened).containsExactly(5L, 2L);
		assertThat(timeline.points()).extracting(TimelinePoint::solved).containsExactly(3L, 0L);
		assertThat(timeline.points().get(0).label()).isEqualTo("Sem. 02/11");
	}

	@Test
	@DisplayName("RF02: datas agrupadas no fuso de São Paulo, não em UTC")
	void timelineUsesBusinessTimezone() {
		Ticket lateNight = TicketFixtures.open(TicketType.EMAIL, TicketPriority.LOW, at("2026-11-10T01:30:00Z"));

		Timeline timeline = DashboardMetrics.timeline(List.of(lateNight), LocalDate.of(2026, 11, 9),
				LocalDate.of(2026, 11, 10), Granularity.DAY, SP);

		assertThat(timeline.points()).extracting(TimelinePoint::opened).containsExactly(1L, 0L);
	}

	@Test
	@DisplayName("RF02: escolhe a granularidade pelo tamanho do período e limita a quantidade de pontos")
	void granularityRules() {
		assertThat(Granularity.auto(30)).isEqualTo(Granularity.DAY);
		assertThat(Granularity.auto(90)).isEqualTo(Granularity.WEEK);
		assertThat(Granularity.auto(365)).isEqualTo(Granularity.MONTH);
		assertThat(Granularity.MONTH.labelOf(LocalDate.of(2026, 11, 1))).isEqualTo("nov/26");

		assertThatThrownBy(() -> DashboardMetrics.timeline(tickets, LocalDate.of(2025, 1, 1),
				LocalDate.of(2026, 11, 9), Granularity.DAY, SP))
				.isInstanceOf(InvalidParameterException.class)
				.hasMessageContaining("400 pontos");
	}

	@Test
	@DisplayName("RF03: todas as situações aparecem, inclusive as zeradas, e backlog por prioridade")
	void statusDistribution() {
		StatusDistribution status = DashboardMetrics.status(tickets, NOW);

		assertThat(status.items()).hasSize(6);
		assertThat(status.items()).extracting(i -> i.code() + "=" + i.count())
				.containsExactly("OPEN=2", "IN_PROGRESS=1", "WAITING_USER=0", "RESOLVED=1", "CLOSED=2", "CANCELED=1");
		assertThat(status.items().get(0).percent()).isEqualTo(28.6);
		assertThat(status.activeByPriority()).extracting(p -> p.code() + ":" + p.active() + "/" + p.overdue())
				.containsExactly("CRITICAL:1/0", "HIGH:0/0", "MEDIUM:1/0", "LOW:1/1");
	}

	@Test
	@DisplayName("RF04: ranking de tipos por chamados atendidos")
	void typeRankingBySolved() {
		TypeRanking ranking = DashboardMetrics.types(tickets, RankingMetric.SOLVED, 10);

		assertThat(ranking.total()).isEqualTo(3);
		assertThat(ranking.items()).extracting(i -> i.code())
				.containsExactly("HARDWARE", "NETWORK", "ACCESS", "EMAIL", "ERP");
		var hardware = ranking.items().get(0);
		assertThat(hardware.position()).isEqualTo(1);
		assertThat(hardware.solved()).isEqualTo(2);
		assertThat(hardware.share()).isEqualTo(66.7);
		assertThat(hardware.avgResolutionHours()).isEqualTo(5.5);
		assertThat(hardware.slaMetPercent()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("RF04: ranking por total e limite de itens")
	void typeRankingByTotalWithLimit() {
		TypeRanking ranking = DashboardMetrics.types(tickets, RankingMetric.TOTAL, 2);

		assertThat(ranking.items()).extracting(i -> i.code()).containsExactly("HARDWARE", "NETWORK");
		assertThat(ranking.total()).isEqualTo(7);
	}

	@Test
	@DisplayName("Desempenho por técnico ordenado por atendidos")
	void technicianRanking() {
		TechnicianRanking ranking = DashboardMetrics.technicians(tickets, NOW, 10);

		assertThat(ranking.unassignedActive()).isEqualTo(2);
		assertThat(ranking.items()).extracting(i -> i.name()).containsExactly("Ana", "Bruno");
		var bruno = ranking.items().get(1);
		assertThat(bruno.assigned()).isEqualTo(3);
		assertThat(bruno.solved()).isEqualTo(1);
		assertThat(bruno.active()).isEqualTo(1);
		assertThat(bruno.overdue()).isEqualTo(1);
		assertThat(bruno.slaMetPercent()).isEqualTo(100.0);
	}

	private static Instant at(String iso) {
		return Instant.parse(iso);
	}
}
