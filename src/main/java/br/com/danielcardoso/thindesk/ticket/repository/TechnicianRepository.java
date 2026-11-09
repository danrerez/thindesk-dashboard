package br.com.danielcardoso.thindesk.ticket.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.danielcardoso.thindesk.ticket.domain.Technician;

public interface TechnicianRepository extends MongoRepository<Technician, String> {

	List<Technician> findByActive(boolean active, Sort sort);
}
