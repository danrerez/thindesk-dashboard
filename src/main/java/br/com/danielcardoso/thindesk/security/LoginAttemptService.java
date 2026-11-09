package br.com.danielcardoso.thindesk.security;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.danielcardoso.thindesk.config.AppProperties;
import br.com.danielcardoso.thindesk.user.repository.UserRepository;
import br.com.danielcardoso.thindesk.user.service.UserService;

@Service
public class LoginAttemptService {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

	private final UserRepository repository;
	private final AppProperties.Security settings;

	public LoginAttemptService(UserRepository repository, AppProperties properties) {
		this.repository = repository;
		this.settings = properties.security();
	}

	public void recordFailure(String email) {
		repository.findByEmail(UserService.normalizeEmail(email)).ifPresent(user -> {
			Instant now = Instant.now();
			if (user.getLockedUntil() != null && !user.isLocked(now)) {
				user.setLockedUntil(null);
				user.setFailedLoginAttempts(0);
			}
			int attempts = user.getFailedLoginAttempts() + 1;
			user.setFailedLoginAttempts(attempts);
			if (attempts >= settings.maxLoginAttempts()) {
				user.setLockedUntil(now.plus(settings.lockDuration()));
				log.warn("Conta {} bloqueada após {} tentativas de login inválidas", user.getEmail(), attempts);
			}
			repository.save(user);
		});
	}

	public void recordSuccess(String email) {
		repository.findByEmail(UserService.normalizeEmail(email)).ifPresent(user -> {
			user.setFailedLoginAttempts(0);
			user.setLockedUntil(null);
			user.setLastLoginAt(Instant.now());
			repository.save(user);
		});
	}
}
