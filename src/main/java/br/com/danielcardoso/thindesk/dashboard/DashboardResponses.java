package br.com.danielcardoso.thindesk.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.Option;

/**
 * Corpos de resposta dos endpoints {@code /api/v1/dashboard/**}. Médias e percentuais vêm {@code null} quando não
 * há dados suficientes para calculá-los (em vez de 0, que seria enganoso).
 */
public final class DashboardResponses {

	private DashboardResponses() {
	}

	public record Period(LocalDate from, LocalDate to) {
	}

	/** RF01: indicadores gerais. */
	public record Summary(
			Period period,
			Totals totals,
			Rates rates,
			Times times,
			Comparison comparison,
			Instant generatedAt) {
	}

	public record Totals(
			long total,
			long active,
			long open,
			long inProgress,
			long waitingUser,
			long solved,
			long canceled,
			long overdue,
			long criticalActive,
			long unassignedActive) {
	}

	/**
	 * @param resolutionRate chamados resolvidos ou fechados sobre o total não cancelado (%)
	 * @param slaMetPercent chamados resolvidos dentro do prazo sobre o total resolvido (%)
	 */
	public record Rates(Double resolutionRate, Double slaMetPercent) {
	}

	public record Times(Double avgFirstResponseHours, Double avgResolutionHours, Double medianResolutionHours) {
	}

	/** Mesmo filtro aplicado ao período imediatamente anterior, com a mesma duração. */
	public record Comparison(
			Period previousPeriod,
			long previousTotal,
			long previousSolved,
			Double totalChangePercent,
			Double solvedChangePercent) {
	}

	/** RF02: série histórica de chamados abertos e atendidos. */
	public record Timeline(
			Option granularity,
			Period period,
			List<TimelinePoint> points,
			long totalOpened,
			long totalSolved) {
	}

	public record TimelinePoint(LocalDate period, String label, long opened, long solved, Double avgResolutionHours) {
	}

	/** RF03: situação dos chamados. */
	public record StatusDistribution(long total, List<StatusItem> items, List<PriorityItem> activeByPriority) {
	}

	public record StatusItem(String code, String label, long count, Double percent, boolean active) {
	}

	public record PriorityItem(String code, String label, int slaHours, long active, long overdue) {
	}

	/** RF04: tipos de chamados mais atendidos. */
	public record TypeRanking(Option metric, long total, List<TypeItem> items) {
	}

	public record TypeItem(
			int position,
			String code,
			String label,
			long total,
			long solved,
			long active,
			Double share,
			Double avgResolutionHours,
			Double slaMetPercent) {
	}

	public record TechnicianRanking(List<TechnicianItem> items, long unassignedActive) {
	}

	public record TechnicianItem(
			int position,
			String id,
			String name,
			long assigned,
			long solved,
			long active,
			long overdue,
			Double avgResolutionHours,
			Double slaMetPercent) {
	}

	/** RF05: opções disponíveis para os filtros. */
	public record Metadata(
			List<Option> statuses,
			List<Option> types,
			List<PriorityOption> priorities,
			List<Option> departments,
			List<TechnicianOption> technicians,
			List<Option> granularities,
			DataRange dataRange,
			String timezone,
			int refreshSeconds) {
	}

	public record PriorityOption(String code, String label, int slaHours) {
	}

	public record TechnicianOption(String id, String name, String level, boolean active) {
	}

	public record DataRange(LocalDate firstOpenedAt, LocalDate lastOpenedAt, long totalTickets) {
	}
}
