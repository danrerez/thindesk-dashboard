package br.com.danielcardoso.thindesk.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.service.TicketFilter;

/**
 * Consultas dinâmicas sobre a coleção de chamados (filtros combináveis do dashboard).
 */
public interface TicketQueryRepository {

	/** Chamados abertos no período e que atendem aos filtros, sem os campos longos (notas e histórico). */
	List<Ticket> findForMetrics(TicketFilter filter, Instant now);

	/**
	 * Chamados que foram abertos <b>ou</b> resolvidos no período, para a série histórica. Os demais filtros são
	 * aplicados normalmente.
	 */
	List<Ticket> findOpenedOrResolvedIn(TicketFilter filter, Instant now);

	Page<Ticket> search(TicketFilter filter, Pageable pageable, Instant now);

	/** Data de abertura do chamado mais antigo e do mais recente. */
	Optional<Instant[]> openedAtRange();

	/** Próximo número sequencial de protocolo (operação atômica no MongoDB). */
	long nextProtocolSequence();

	void resetProtocolSequence(long value);
}
