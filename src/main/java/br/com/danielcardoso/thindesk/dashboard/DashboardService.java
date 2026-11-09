package br.com.danielcardoso.thindesk.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.com.danielcardoso.thindesk.config.AppProperties;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Comparison;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.DataRange;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Metadata;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Period;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.PriorityOption;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.StatusDistribution;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Summary;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianOption;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianRanking;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Timeline;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TypeRanking;
import br.com.danielcardoso.thindesk.ticket.domain.Department;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.Option;
import br.com.danielcardoso.thindesk.ticket.dto.TicketSearchParams;
import br.com.danielcardoso.thindesk.ticket.repository.TechnicianRepository;
import br.com.danielcardoso.thindesk.ticket.repository.TicketRepository;
import br.com.danielcardoso.thindesk.ticket.service.TicketFilter;

/**
 * Orquestra as consultas do dashboard: o MongoDB aplica os filtros e devolve só os campos necessários, e
 * {@link DashboardMetrics} calcula os indicadores.
 */
@Service
public class DashboardService {

	private final TicketRepository tickets;
	private final TechnicianRepository technicians;
	private final Clock clock;
	private final ZoneId zone;
	private final int refreshSeconds;

	public DashboardService(TicketRepository tickets, TechnicianRepository technicians, Clock clock,
			AppProperties properties) {
		this.tickets = tickets;
		this.technicians = technicians;
		this.clock = clock;
		this.zone = properties.dashboard().zone();
		this.refreshSeconds = properties.dashboard().refreshSeconds();
	}

	public ZoneId zone() {
		return zone;
	}

	public Summary summary(TicketSearchParams params) {
		Instant now = clock.instant();
		TicketFilter filter = params.toFilter(zone);
		List<Ticket> current = tickets.findForMetrics(filter, now);
		var totals = DashboardMetrics.totals(current, now);

		Comparison comparison = null;
		if (params.from() != null && params.to() != null) {
			long days = ChronoUnit.DAYS.between(params.from(), params.to()) + 1;
			LocalDate previousFrom = params.from().minusDays(days);
			LocalDate previousTo = params.from().minusDays(1);
			List<Ticket> previous = tickets.findForMetrics(filter.withPeriod(
					previousFrom.atStartOfDay(zone).toInstant(), filter.from()), now);
			long previousSolved = previous.stream().filter(t -> t.getStatus().isSolved()).count();
			comparison = new Comparison(new Period(previousFrom, previousTo), previous.size(), previousSolved,
					DashboardMetrics.change(previous.size(), totals.total()),
					DashboardMetrics.change(previousSolved, totals.solved()));
		}
		return new Summary(new Period(params.from(), params.to()), totals, DashboardMetrics.rates(current),
				DashboardMetrics.times(current), comparison, now);
	}

	public Timeline timeline(TicketSearchParams params, Granularity granularity) {
		Instant now = clock.instant();
		List<Ticket> list = tickets.findOpenedOrResolvedIn(params.toFilter(zone), now);
		return DashboardMetrics.timeline(list, params.from(), params.to(), granularity, zone);
	}

	public StatusDistribution status(TicketSearchParams params) {
		Instant now = clock.instant();
		return DashboardMetrics.status(tickets.findForMetrics(params.toFilter(zone), now), now);
	}

	public TypeRanking types(TicketSearchParams params, RankingMetric metric, int limit) {
		Instant now = clock.instant();
		return DashboardMetrics.types(tickets.findForMetrics(params.toFilter(zone), now), metric, limit);
	}

	public TechnicianRanking technicians(TicketSearchParams params, int limit) {
		Instant now = clock.instant();
		return DashboardMetrics.technicians(tickets.findForMetrics(params.toFilter(zone), now), now, limit);
	}

	public Metadata metadata() {
		DataRange range = tickets.openedAtRange()
				.map(r -> new DataRange(r[0].atZone(zone).toLocalDate(), r[1].atZone(zone).toLocalDate(),
						tickets.count()))
				.orElse(new DataRange(null, null, 0));
		return new Metadata(
				Arrays.stream(TicketStatus.values()).map(Option::of).toList(),
				Arrays.stream(TicketType.values()).map(Option::of).toList(),
				Arrays.stream(TicketPriority.values())
						.map(p -> new PriorityOption(p.name(), p.getLabel(), p.getSlaHours())).toList(),
				Arrays.stream(Department.values()).map(Option::of).toList(),
				technicians.findAll(Sort.by("name")).stream()
						.map(t -> new TechnicianOption(t.getId(), t.getName(), t.getLevel().name(), t.isActive()))
						.toList(),
				Arrays.stream(Granularity.values()).map(g -> new Option(g.name(), g.getLabel())).toList(),
				range, zone.getId(), refreshSeconds);
	}
}
