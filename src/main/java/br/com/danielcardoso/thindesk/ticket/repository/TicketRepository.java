package br.com.danielcardoso.thindesk.ticket.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.danielcardoso.thindesk.ticket.domain.Ticket;

public interface TicketRepository extends MongoRepository<Ticket, String>, TicketQueryRepository {
}
