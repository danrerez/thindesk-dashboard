package br.com.danielcardoso.thindesk.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import br.com.danielcardoso.thindesk.common.exception.InvalidParameterException;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Period;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.PriorityItem;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Rates;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.StatusDistribution;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.StatusItem;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianItem;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianRanking;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Timeline;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TimelinePoint;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Times;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Totals;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TypeItem;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TypeRanking;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.Option;

/**
 * Cálculo dos indicadores do dashboard. Funções puras sobre a lista de chamados já filtrada pelo banco: não acessam
 * repositório nem relógio do sistema, o que permite testá-las com cenários montados à mão.
 */
public final class DashboardMetrics {

	private static final double MILLIS_PER_HOUR = 3_600_000d;

	private DashboardMetrics() {
	}

	public static Totals totals(List<Ticket> tickets, Instant now) {
		Map<TicketStatus, Long> byStatus = countByStatus(tickets);
		long active = tickets.stream().filter(t -> t.getStatus().isActive()).count();
		return new Totals(
				tickets.size(),
				active,
				byStatus.get(TicketStatus.OPEN),
				byStatus.get(TicketStatus.IN_PROGRESS),
				byStatus.get(TicketStatus.WAITING_USER),
				byStatus.get(TicketStatus.RESOLVED) + byStatus.get(TicketStatus.CLOSED),
				byStatus.get(TicketStatus.CANCELED),
				tickets.stream().filter(t -> t.getStatus().isActive() && t.isOverdue(now)).count(),
				tickets.stream().filter(t -> t.getStatus().isActive() && t.getPriority() == TicketPriority.CRITICAL)
						.count(),
				tickets.stream().filter(t -> t.getStatus().isActive() && t.getTechnicianId() == null).count());
	}

	public static Rates rates(List<Ticket> tickets) {
		long notCanceled = tickets.stream().filter(t -> t.getStatus() != TicketStatus.CANCELED).count();
		long solved = tickets.stream().filter(t -> t.getStatus().isSolved()).count();
		return new Rates(percent(solved, notCanceled), slaMetPercent(tickets));
	}

	public static Times times(List<Ticket> tickets) {
		List<Double> resolution = hours(tickets, Ticket::resolutionTime);
		return new Times(average(hours(tickets, Ticket::firstResponseTime)), average(resolution), median(resolution));
	}

	/** Variação percentual entre dois valores; {@code null} quando o valor anterior é zero. */
	public static Double change(long previous, long current) {
		if (previous == 0) {
			return null;
		}
		return round((current - previous) * 100d / previous);
	}

	/**
	 * Série histórica: abertos contados pela data de abertura e atendidos pela data de solução, cada um dentro do
	 * período. Intervalos sem movimento aparecem com zero para o gráfico não "pular" datas.
	 *
	 * @param from primeiro dia (inclusivo) ou {@code null} para usar o dado mais antigo
	 * @param to último dia (inclusivo) ou {@code null} para usar o dado mais recente
	 * @param requested granularidade pedida ou {@code null} para escolher automaticamente
	 */
	public static Timeline timeline(List<Ticket> tickets, LocalDate from, LocalDate to, Granularity requested,
			ZoneId zone) {
		LocalDate start = from != null ? from : earliest(tickets, zone);
		LocalDate end = to != null ? to : latest(tickets, zone);
		if (start == null || end == null) {
			Granularity granularity = requested != null ? requested : Granularity.DAY;
			return new Timeline(option(granularity), new Period(from, to), List.of(), 0, 0);
		}
		long days = ChronoUnit.DAYS.between(start, end) + 1;
		Granularity granularity = requested != null ? requested : Granularity.auto(days);

		Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
		for (LocalDate b = granularity.bucketOf(start); !b.isAfter(end); b = granularity.next(b)) {
			buckets.put(b, new Bucket());
			if (buckets.size() > Granularity.MAX_POINTS) {
				throw new InvalidParameterException("granularity", "O período gera mais de " + Granularity.MAX_POINTS
						+ " pontos com granularidade " + granularity.name() + ". Use WEEK ou MONTH.");
			}
		}

		long totalOpened = 0;
		long totalSolved = 0;
		for (Ticket t : tickets) {
			LocalDate opened = t.getOpenedAt().atZone(zone).toLocalDate();
			if (within(opened, start, end)) {
				buckets.get(granularity.bucketOf(opened)).opened++;
				totalOpened++;
			}
			if (t.getStatus().isSolved() && t.getResolvedAt() != null) {
				LocalDate solved = t.getResolvedAt().atZone(zone).toLocalDate();
				if (within(solved, start, end)) {
					Bucket bucket = buckets.get(granularity.bucketOf(solved));
					bucket.solved++;
					bucket.resolutionHours.add(toHours(t.resolutionTime()));
					totalSolved++;
				}
			}
		}

		List<TimelinePoint> points = buckets.entrySet().stream()
				.map(e -> new TimelinePoint(e.getKey(), granularity.labelOf(e.getKey()), e.getValue().opened,
						e.getValue().solved, average(e.getValue().resolutionHours)))
				.toList();
		return new Timeline(option(granularity), new Period(start, end), points, totalOpened, totalSolved);
	}

	public static StatusDistribution status(List<Ticket> tickets, Instant now) {
		Map<TicketStatus, Long> byStatus = countByStatus(tickets);
		List<StatusItem> items = Arrays.stream(TicketStatus.values())
				.map(s -> new StatusItem(s.name(), s.getLabel(), byStatus.get(s), percent(byStatus.get(s),
						tickets.size()), s.isActive()))
				.toList();
		List<PriorityItem> priorities = Arrays.stream(TicketPriority.values())
				.sorted(Comparator.comparingInt(TicketPriority::ordinal).reversed())
				.map(p -> {
					List<Ticket> active = tickets.stream()
							.filter(t -> t.getStatus().isActive() && t.getPriority() == p)
							.toList();
					return new PriorityItem(p.name(), p.getLabel(), p.getSlaHours(), active.size(),
							active.stream().filter(t -> t.isOverdue(now)).count());
				})
				.toList();
		return new StatusDistribution(tickets.size(), items, priorities);
	}

	public static TypeRanking types(List<Ticket> tickets, RankingMetric metric, int limit) {
		Map<TicketType, List<Ticket>> byType = tickets.stream()
				.collect(Collectors.groupingBy(Ticket::getType, () -> new EnumMap<>(TicketType.class),
						Collectors.toList()));
		Function<List<Ticket>, Long> value = metric == RankingMetric.SOLVED
				? list -> list.stream().filter(t -> t.getStatus().isSolved()).count()
				: list -> (long) list.size();
		long metricTotal = byType.values().stream().mapToLong(value::apply).sum();

		List<Map.Entry<TicketType, List<Ticket>>> ordered = byType.entrySet().stream()
				.sorted(Comparator.<Map.Entry<TicketType, List<Ticket>>>comparingLong(e -> value.apply(e.getValue()))
						.reversed()
						.thenComparing(e -> e.getValue().size(), Comparator.reverseOrder())
						.thenComparing(e -> e.getKey().getLabel()))
				.limit(limit)
				.toList();

		List<TypeItem> items = new ArrayList<>();
		for (Map.Entry<TicketType, List<Ticket>> entry : ordered) {
			List<Ticket> list = entry.getValue();
			items.add(new TypeItem(items.size() + 1, entry.getKey().name(), entry.getKey().getLabel(), list.size(),
					count(list, t -> t.getStatus().isSolved()), count(list, t -> t.getStatus().isActive()),
					percent(value.apply(list), metricTotal), average(hours(list, Ticket::resolutionTime)),
					slaMetPercent(list)));
		}
		return new TypeRanking(new Option(metric.name(), metric.getLabel()), metricTotal, items);
	}

	public static TechnicianRanking technicians(List<Ticket> tickets, Instant now, int limit) {
		Map<String, List<Ticket>> byTechnician = tickets.stream()
				.filter(t -> t.getTechnicianId() != null)
				.collect(Collectors.groupingBy(Ticket::getTechnicianId));

		List<TechnicianItem> unordered = byTechnician.entrySet().stream()
				.map(e -> {
					List<Ticket> list = e.getValue();
					return new TechnicianItem(0, e.getKey(), list.get(0).getTechnicianName(), list.size(),
							count(list, t -> t.getStatus().isSolved()), count(list, t -> t.getStatus().isActive()),
							count(list, t -> t.getStatus().isActive() && t.isOverdue(now)),
							average(hours(list, Ticket::resolutionTime)), slaMetPercent(list));
				})
				.sorted(Comparator.comparingLong(TechnicianItem::solved).reversed()
						.thenComparing(TechnicianItem::avgResolutionHours,
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(TechnicianItem::name))
				.limit(limit)
				.toList();

		List<TechnicianItem> items = new ArrayList<>();
		for (TechnicianItem i : unordered) {
			items.add(new TechnicianItem(items.size() + 1, i.id(), i.name(), i.assigned(), i.solved(), i.active(),
					i.overdue(), i.avgResolutionHours(), i.slaMetPercent()));
		}
		long unassigned = count(tickets, t -> t.getStatus().isActive() && t.getTechnicianId() == null);
		return new TechnicianRanking(items, unassigned);
	}

	static Double slaMetPercent(List<Ticket> tickets) {
		List<Boolean> results = tickets.stream().map(Ticket::slaMet).filter(Objects::nonNull).toList();
		return percent(results.stream().filter(Boolean::booleanValue).count(), results.size());
	}

	static Double percent(long part, long total) {
		return total == 0 ? null : round(part * 100d / total);
	}

	static Double average(List<Double> values) {
		return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
	}

	static Double median(List<Double> values) {
		if (values.isEmpty()) {
			return null;
		}
		List<Double> sorted = values.stream().sorted().toList();
		int middle = sorted.size() / 2;
		double median = sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
		return round(median);
	}

	static double round(double value) {
		return Math.round(value * 10d) / 10d;
	}

	private static Map<TicketStatus, Long> countByStatus(List<Ticket> tickets) {
		Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
		for (TicketStatus status : TicketStatus.values()) {
			counts.put(status, 0L);
		}
		tickets.forEach(t -> counts.merge(t.getStatus(), 1L, Long::sum));
		return counts;
	}

	private static List<Double> hours(List<Ticket> tickets, Function<Ticket, Duration> duration) {
		return tickets.stream().map(duration).filter(Objects::nonNull).map(DashboardMetrics::toHours).toList();
	}

	private static double toHours(Duration duration) {
		return duration.toMillis() / MILLIS_PER_HOUR;
	}

	private static long count(List<Ticket> tickets, Predicate<Ticket> predicate) {
		return tickets.stream().filter(predicate).count();
	}

	private static boolean within(LocalDate date, LocalDate start, LocalDate end) {
		return !date.isBefore(start) && !date.isAfter(end);
	}

	private static LocalDate earliest(List<Ticket> tickets, ZoneId zone) {
		return tickets.stream().map(Ticket::getOpenedAt).min(Comparator.naturalOrder())
				.map(i -> i.atZone(zone).toLocalDate()).orElse(null);
	}

	private static LocalDate latest(List<Ticket> tickets, ZoneId zone) {
		return tickets.stream()
				.flatMap(t -> java.util.stream.Stream.of(t.getOpenedAt(), t.getResolvedAt()))
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.map(i -> i.atZone(zone).toLocalDate()).orElse(null);
	}

	private static Option option(Granularity granularity) {
		return new Option(granularity.name(), granularity.getLabel());
	}

	private static final class Bucket {
		long opened;
		long solved;
		final List<Double> resolutionHours = new ArrayList<>();
	}
}
