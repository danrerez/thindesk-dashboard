package br.com.danielcardoso.thindesk.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Metadata;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.StatusDistribution;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Summary;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TechnicianRanking;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Timeline;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.TypeRanking;
import br.com.danielcardoso.thindesk.dashboard.DashboardService;
import br.com.danielcardoso.thindesk.dashboard.Granularity;
import br.com.danielcardoso.thindesk.dashboard.RankingMetric;
import br.com.danielcardoso.thindesk.ticket.dto.TicketSearchParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Dashboard", description = "Indicadores, gráficos e rankings do painel administrativo")
public class DashboardApiController {

	private final DashboardService dashboard;

	public DashboardApiController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/summary")
	@Operation(summary = "RF01: indicadores gerais",
			description = "Totais por situação, backlog, atrasados, taxa de resolução, SLA, tempos médios e "
					+ "comparação com o período anterior (quando from e to são informados).")
	public Summary summary(@Valid @ModelAttribute TicketSearchParams params) {
		return dashboard.summary(params);
	}

	@GetMapping("/timeline")
	@Operation(summary = "RF02: chamados abertos e atendidos por período",
			description = "Série histórica agrupada por dia, semana ou mês. Sem granularity, a API escolhe pelo "
					+ "tamanho do período (até 45 dias: DAY; até 200: WEEK; acima: MONTH).")
	public Timeline timeline(@Valid @ModelAttribute TicketSearchParams params,
			@Parameter(description = "DAY, WEEK ou MONTH") @RequestParam(required = false) Granularity granularity) {
		return dashboard.timeline(params, granularity);
	}

	@GetMapping("/status")
	@Operation(summary = "RF03: situação dos chamados",
			description = "Quantidade e percentual por situação e backlog por prioridade com atrasados.")
	public StatusDistribution status(@Valid @ModelAttribute TicketSearchParams params) {
		return dashboard.status(params);
	}

	@GetMapping("/types")
	@Operation(summary = "RF04: tipos de chamados mais atendidos",
			description = "Ranking por tipo com total, atendidos, em aberto, participação, tempo médio e SLA.")
	public TypeRanking types(@Valid @ModelAttribute TicketSearchParams params,
			@Parameter(description = "SOLVED (padrão) ou TOTAL") @RequestParam(defaultValue = "SOLVED") RankingMetric metric,
			@RequestParam(defaultValue = "8") @Min(value = 1, message = "limit deve ser no mínimo 1")
			@Max(value = 20, message = "limit deve ser no máximo 20") int limit) {
		return dashboard.types(params, metric, limit);
	}

	@GetMapping("/technicians")
	@Operation(summary = "Desempenho por técnico",
			description = "Chamados atribuídos, atendidos, em aberto e atrasados, tempo médio e SLA por técnico.")
	public TechnicianRanking technicians(@Valid @ModelAttribute TicketSearchParams params,
			@RequestParam(defaultValue = "10") @Min(value = 1, message = "limit deve ser no mínimo 1")
			@Max(value = 50, message = "limit deve ser no máximo 50") int limit) {
		return dashboard.technicians(params, limit);
	}

	@GetMapping("/filters")
	@Operation(summary = "RF05: opções dos filtros",
			description = "Situações, tipos, prioridades (com SLA), setores, técnicos, granularidades e o "
					+ "intervalo de datas com dados.")
	public Metadata filters() {
		return dashboard.metadata();
	}
}
