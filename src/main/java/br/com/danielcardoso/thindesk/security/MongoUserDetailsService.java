package br.com.danielcardoso.thindesk.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import br.com.danielcardoso.thindesk.user.repository.UserRepository;
import br.com.danielcardoso.thindesk.user.service.UserService;

@Service
public class MongoUserDetailsService implements UserDetailsService {

	private final UserRepository repository;

	public MongoUserDetailsService(UserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return repository.findByEmail(UserService.normalizeEmail(email))
				.map(AuthenticatedUser::new)
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
	}
}
