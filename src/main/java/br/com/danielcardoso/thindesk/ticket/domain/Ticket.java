package br.com.danielcardoso.thindesk.ticket.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;

/**
 * Chamado técnico. O nome do técnico é guardado junto com o id (desnormalizado) para que as consultas do dashboard
 * não precisem cruzar coleções.
 */
@Document("tickets")
@CompoundIndex(name = "status_openedAt", def = "{'status': 1, 'openedAt': -1}")
public class Ticket {

	@Id
	private String id;

	@Version
	private Long version;

	@Indexed(unique = true)
	private String protocol;

	private String title;

	private String description;

	@Indexed
	private TicketType type;

	private TicketPriority priority;

	private TicketStatus status = TicketStatus.OPEN;

	private String requesterName;

	private String requesterEmail;

	private Department department;

	@Indexed
	private String technicianId;

	private String technicianName;

	@Indexed
	private Instant openedAt;

	private Instant dueAt;

	private Instant firstResponseAt;

	private Instant resolvedAt;

	private Instant closedAt;

	private List<TechnicalNote> notes = new ArrayList<>();

	private List<StatusChange> history = new ArrayList<>();

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public Ticket() {
	}

	public Ticket(String protocol, String title, String description, TicketType type, TicketPriority priority,
			String requesterName, String requesterEmail, Department department, Instant openedAt) {
		this.protocol = protocol;
		this.title = title;
		this.description = description;
		this.type = type;
		this.priority = priority;
		this.requesterName = requesterName;
		this.requesterEmail = requesterEmail;
		this.department = department;
		this.openedAt = openedAt;
		this.dueAt = openedAt.plus(priority.sla());
	}

	/**
	 * Aplica uma mudança de situação respeitando o fluxo definido em {@link TicketStatus} e registra as datas usadas
	 * nos indicadores (primeira resposta, solução e fechamento).
	 */
	public void changeStatus(TicketStatus target, String changedBy, Instant now) {
		if (status == target) {
			throw new BusinessException("O chamado já está com a situação " + target.getLabel() + ".");
		}
		if (!status.canMoveTo(target)) {
			throw new BusinessException("Não é possível passar o chamado de " + status.getLabel() + " para "
					+ target.getLabel() + ".");
		}
		if (target == TicketStatus.IN_PROGRESS && technicianId == null) {
			throw new BusinessException("Atribua um técnico antes de iniciar o atendimento.");
		}
		if (target == TicketStatus.IN_PROGRESS && firstResponseAt == null) {
			firstResponseAt = now;
		}
		if (target == TicketStatus.RESOLVED) {
			resolvedAt = now;
		}
		if (status == TicketStatus.RESOLVED && target == TicketStatus.IN_PROGRESS) {
			resolvedAt = null;
		}
		if (target == TicketStatus.CLOSED || target == TicketStatus.CANCELED) {
			closedAt = now;
		}
		history.add(new StatusChange(status, target, changedBy, now));
		status = target;
	}

	public void assignTo(Technician technician) {
		if (status.isFinal()) {
			throw new BusinessException("Chamados fechados ou cancelados não podem ser reatribuídos.");
		}
		if (!technician.isActive()) {
			throw new BusinessException("O técnico " + technician.getName() + " está inativo.");
		}
		this.technicianId = technician.getId();
		this.technicianName = technician.getName();
	}

	public void addNote(TechnicalNote note) {
		if (status.isFinal()) {
			throw new BusinessException("Chamados fechados ou cancelados não recebem novas considerações.");
		}
		notes.add(note);
	}

	public boolean isOverdue(Instant now) {
		if (dueAt == null) {
			return false;
		}
		if (resolvedAt != null) {
			return resolvedAt.isAfter(dueAt);
		}
		return status.isActive() && now.isAfter(dueAt);
	}

	/** SLA cumprido: só faz sentido para chamados já resolvidos. */
	public Boolean slaMet() {
		if (resolvedAt == null || dueAt == null) {
			return null;
		}
		return !resolvedAt.isAfter(dueAt);
	}

	public Duration resolutionTime() {
		return resolvedAt == null ? null : Duration.between(openedAt, resolvedAt);
	}

	public Duration firstResponseTime() {
		return firstResponseAt == null ? null : Duration.between(openedAt, firstResponseAt);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TicketType getType() {
		return type;
	}

	public void setType(TicketType type) {
		this.type = type;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public void setPriority(TicketPriority priority) {
		this.priority = priority;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public void setStatus(TicketStatus status) {
		this.status = status;
	}

	public String getRequesterName() {
		return requesterName;
	}

	public void setRequesterName(String requesterName) {
		this.requesterName = requesterName;
	}

	public String getRequesterEmail() {
		return requesterEmail;
	}

	public void setRequesterEmail(String requesterEmail) {
		this.requesterEmail = requesterEmail;
	}

	public Department getDepartment() {
		return department;
	}

	public void setDepartment(Department department) {
		this.department = department;
	}

	public String getTechnicianId() {
		return technicianId;
	}

	public void setTechnicianId(String technicianId) {
		this.technicianId = technicianId;
	}

	public String getTechnicianName() {
		return technicianName;
	}

	public void setTechnicianName(String technicianName) {
		this.technicianName = technicianName;
	}

	public Instant getOpenedAt() {
		return openedAt;
	}

	public void setOpenedAt(Instant openedAt) {
		this.openedAt = openedAt;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public void setDueAt(Instant dueAt) {
		this.dueAt = dueAt;
	}

	public Instant getFirstResponseAt() {
		return firstResponseAt;
	}

	public void setFirstResponseAt(Instant firstResponseAt) {
		this.firstResponseAt = firstResponseAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public List<TechnicalNote> getNotes() {
		return notes;
	}

	public void setNotes(List<TechnicalNote> notes) {
		this.notes = notes;
	}

	public List<StatusChange> getHistory() {
		return history;
	}

	public void setHistory(List<StatusChange> history) {
		this.history = history;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
