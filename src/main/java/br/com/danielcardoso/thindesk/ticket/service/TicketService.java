package br.com.danielcardoso.thindesk.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import br.com.danielcardoso.thindesk.ticket.domain.TechnicalNote;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketStatus;
import br.com.danielcardoso.thindesk.ticket.dto.TicketRequests;
import br.com.danielcardoso.thindesk.ticket.repository.TechnicianRepository;
import br.com.danielcardoso.thindesk.ticket.repository.TicketRepository;

@Service
public class TicketService {

	private final TicketRepository tickets;
	private final TechnicianRepository technicians;
	private final Clock clock;

	public TicketService(TicketRepository tickets, TechnicianRepository technicians, Clock clock) {
		this.tickets = tickets;
		this.technicians = technicians;
		this.clock = clock;
	}

	public Instant now() {
		return clock.instant();
	}

	public Ticket get(String id) {
		return tickets.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Chamado " + id + " não encontrado."));
	}

	public Page<Ticket> search(TicketFilter filter, Pageable pageable) {
		return tickets.search(filter, pageable, now());
	}

	public Ticket create(TicketRequests.CreateTicket request, String author) {
		Instant now = now();
		int year = now.atZone(clock.getZone()).getYear();
		String protocol = "TD-%d-%06d".formatted(year, tickets.nextProtocolSequence());
		Ticket ticket = new Ticket(protocol, request.title().trim(), request.description().trim(), request.type(),
				request.priority(), request.requesterName().trim(),
				request.requesterEmail().trim().toLowerCase(Locale.ROOT), request.department(), now);
		if (StringUtils.hasText(request.technicianId())) {
			ticket.assignTo(findTechnician(request.technicianId()));
		}
		ticket.addNote(new TechnicalNote(author, "Chamado registrado pelo painel administrativo.", now));
		return tickets.save(ticket);
	}

	public Ticket changeStatus(String id, TicketRequests.ChangeStatus request, String author) {
		Ticket ticket = get(id);
		TicketStatus target = request.status();
		boolean hasNote = StringUtils.hasText(request.note());
		if ((target == TicketStatus.RESOLVED || target == TicketStatus.CANCELED) && !hasNote) {
			throw new BusinessException(target == TicketStatus.RESOLVED
					? "Registre a solução aplicada (campo note) para resolver o chamado."
					: "Informe o motivo do cancelamento (campo note).");
		}
		Instant now = now();
		if (hasNote) {
			ticket.addNote(new TechnicalNote(author, request.note().trim(), now));
		}
		ticket.changeStatus(target, author, now);
		return tickets.save(ticket);
	}

	public Ticket assign(String id, String technicianId, String author) {
		Ticket ticket = get(id);
		Technician technician = findTechnician(technicianId);
		if (technician.getId().equals(ticket.getTechnicianId())) {
			throw new BusinessException("O chamado já está atribuído a " + technician.getName() + ".");
		}
		String previous = ticket.getTechnicianName();
		ticket.assignTo(technician);
		String text = previous == null
				? "Chamado atribuído a " + technician.getName() + "."
				: "Chamado transferido de " + previous + " para " + technician.getName() + ".";
		ticket.addNote(new TechnicalNote(author, text, now()));
		return tickets.save(ticket);
	}

	public Ticket addNote(String id, String text, String author) {
		Ticket ticket = get(id);
		ticket.addNote(new TechnicalNote(author, text.trim(), now()));
		return tickets.save(ticket);
	}

	public List<Technician> listTechnicians(Boolean active) {
		Sort byName = Sort.by("name");
		return active == null ? technicians.findAll(byName) : technicians.findByActive(active, byName);
	}

	private Technician findTechnician(String technicianId) {
		return technicians.findById(technicianId)
				.orElseThrow(() -> new BusinessException("Técnico " + technicianId + " não encontrado."));
	}
}
