package br.com.danielcardoso.thindesk.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import br.com.danielcardoso.thindesk.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthenticationRefresher {

	private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

	public void refresh(User user, HttpServletRequest request, HttpServletResponse response) {
		AuthenticatedUser principal = new AuthenticatedUser(user);
		principal.eraseCredentials();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				principal, null, principal.getAuthorities()));
		SecurityContextHolder.setContext(context);
		repository.saveContext(context, request, response);
	}
}
