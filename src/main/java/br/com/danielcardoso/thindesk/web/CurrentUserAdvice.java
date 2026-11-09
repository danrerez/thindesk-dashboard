package br.com.danielcardoso.thindesk.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import br.com.danielcardoso.thindesk.security.AuthenticatedUser;
import br.com.danielcardoso.thindesk.user.domain.Role;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class CurrentUserAdvice {

	@ModelAttribute("currentUserName")
	public String currentUserName(Authentication authentication) {
		if (authentication == null) {
			return null;
		}
		if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user.getDisplayName();
		}
		return authentication.getName();
	}

	@ModelAttribute("currentUserRole")
	public Role currentUserRole(Authentication authentication) {
		if (authentication == null) {
			return null;
		}
		Set<String> authorities = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
		return Arrays.stream(Role.values())
				.filter(role -> authorities.contains(role.authority()))
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	@ModelAttribute("currentPath")
	public String currentPath(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}
}
