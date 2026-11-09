package br.com.danielcardoso.thindesk.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.danielcardoso.thindesk.common.exception.InvalidParameterException;
import br.com.danielcardoso.thindesk.security.AuthenticationUtils;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.dto.TicketRequests;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.NoteResponse;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.PageResponse;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.TechnicianResponse;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.TicketDetail;
import br.com.danielcardoso.thindesk.ticket.dto.TicketResponses.TicketSummary;
import br.com.danielcardoso.thindesk.ticket.dto.TicketSearchParams;
import br.com.danielcardoso.thindesk.ticket.service.TicketService;
import br.com.danielcardoso.thindesk.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Chamados", description = "Consulta e atualização dos chamados técnicos")
public class TicketApiController {

	static final Set<String> SORTABLE = Set.of("openedAt", "dueAt", "resolvedAt", "protocol", "status", "type");

	private final TicketService tickets;
	private final DashboardService dashboard;

	public TicketApiController(TicketService tickets, DashboardService dashboard) {
		this.tickets = tickets;
		this.dashboard = dashboard;
	}

	@GetMapping("/tickets")
	@Operation(summary = "Lista chamados com filtros e paginação")
	public PageResponse<TicketSummary> list(@Valid @ModelAttribute TicketSearchParams params,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "page deve ser maior ou igual a 0") int page,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "size deve ser no mínimo 1")
			@Max(value = 100, message = "size deve ser no máximo 100") int size,
			@Parameter(description = "campo,direção. Campos: " + "openedAt, dueAt, resolvedAt, protocol, status, type")
			@RequestParam(defaultValue = "openedAt,desc") String sort) {
		Pageable pageable = PageRequest.of(page, size, parseSort(sort));
		var result = tickets.search(params.toFilter(dashboard.zone()), pageable);
		var now = tickets.now();
		return PageResponse.of(result, t -> TicketSummary.from(t, now));
	}

	@GetMapping("/tickets/{id}")
	@Operation(summary = "Detalhe do chamado com considerações técnicas e histórico")
	public TicketDetail get(@PathVariable String id) {
		return detail(tickets.get(id));
	}

	@PostMapping("/tickets")
	@Operation(summary = "Abre um novo chamado")
	public ResponseEntity<TicketDetail> create(@Valid @RequestBody TicketRequests.CreateTicket request,
			Authentication authentication) {
		Ticket created = tickets.create(request, AuthenticationUtils.displayName(authentication));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(created.getId()).toUri();
		return ResponseEntity.created(location).body(detail(created));
	}

	@PatchMapping("/tickets/{id}/status")
	@Operation(summary = "Altera a situação do chamado",
			description = "Respeita o fluxo de situações. Resolver ou cancelar exige uma consideração técnica "
					+ "(note). Iniciar o atendimento exige técnico atribuído.")
	public TicketDetail changeStatus(@PathVariable String id, @Valid @RequestBody TicketRequests.ChangeStatus request,
			Authentication authentication) {
		return detail(tickets.changeStatus(id, request, AuthenticationUtils.displayName(authentication)));
	}

	@PatchMapping("/tickets/{id}/assignment")
	@Operation(summary = "Atribui ou transfere o chamado para um técnico")
	public TicketDetail assign(@PathVariable String id, @Valid @RequestBody TicketRequests.Assign request,
			Authentication authentication) {
		return detail(tickets.assign(id, request.technicianId(), AuthenticationUtils.displayName(authentication)));
	}

	@PostMapping("/tickets/{id}/notes")
	@Operation(summary = "Registra uma consideração técnica")
	public ResponseEntity<NoteResponse> addNote(@PathVariable String id,
			@Valid @RequestBody TicketRequests.AddNote request, Authentication authentication) {
		Ticket ticket = tickets.addNote(id, request.text(), AuthenticationUtils.displayName(authentication));
		var note = ticket.getNotes().get(ticket.getNotes().size() - 1);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/tickets/{id}")
				.buildAndExpand(id).toUri();
		return ResponseEntity.created(location).body(new NoteResponse(note.author(), note.text(), note.createdAt()));
	}

	@GetMapping("/technicians")
	@Tag(name = "Técnicos")
	@Operation(summary = "Lista os técnicos (filtro opcional por ativos/inativos)")
	public List<TechnicianResponse> technicians(@RequestParam(required = false) Boolean active) {
		return tickets.listTechnicians(active).stream().map(TechnicianResponse::from).toList();
	}

	private TicketDetail detail(Ticket ticket) {
		return TicketDetail.from(ticket, tickets.now());
	}

	static Sort parseSort(String sort) {
		String[] parts = sort.split(",");
		String field = parts[0].trim();
		if (!SORTABLE.contains(field)) {
			throw new InvalidParameterException("sort", "Campo de ordenação inválido: '" + field
					+ "'. Use um destes: " + String.join(", ", SORTABLE.stream().sorted().toList()) + ".");
		}
		String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
		if (!direction.equals("asc") && !direction.equals("desc")) {
			throw new InvalidParameterException("sort", "Direção de ordenação inválida: '" + direction
					+ "'. Use asc ou desc.");
		}
		Sort primary = Sort.by(direction.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, field);
		return field.equals("protocol") ? primary : primary.and(Sort.by(Sort.Direction.DESC, "protocol"));
	}
}
