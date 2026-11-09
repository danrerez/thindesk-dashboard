package br.com.danielcardoso.thindesk.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	long countByRole(Role role);

	List<User> findAllByOrderByCreatedAtDesc();
}
