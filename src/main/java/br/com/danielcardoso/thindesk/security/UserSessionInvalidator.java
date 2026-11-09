package br.com.danielcardoso.thindesk.security;

import org.mongodb.spring.session.MongoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

import br.com.danielcardoso.thindesk.user.service.UserAccessChangedEvent;

@Component
public class UserSessionInvalidator {

	private static final Logger log = LoggerFactory.getLogger(UserSessionInvalidator.class);

	private final FindByIndexNameSessionRepository<MongoSession> sessions;

	public UserSessionInvalidator(FindByIndexNameSessionRepository<MongoSession> sessions) {
		this.sessions = sessions;
	}

	@EventListener
	public void onAccessChanged(UserAccessChangedEvent event) {
		var userSessions = sessions.findByPrincipalName(event.email());
		userSessions.keySet().forEach(sessions::deleteById);
		if (!userSessions.isEmpty()) {
			log.info("{} sessão(ões) encerrada(s) para {}", userSessions.size(), event.email());
		}
	}

	public void invalidateOtherSessions(String email, String currentSessionId) {
		sessions.findByPrincipalName(email).keySet().stream()
				.filter(id -> !id.equals(currentSessionId))
				.forEach(sessions::deleteById);
	}
}
