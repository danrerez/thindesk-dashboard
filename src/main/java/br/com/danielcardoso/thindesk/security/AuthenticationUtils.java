package br.com.danielcardoso.thindesk.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import br.com.danielcardoso.thindesk.user.domain.Role;

public final class AuthenticationUtils {

	public static final String USERS_PAGE = "/users";
	public static final String DASHBOARD_PAGE = "/dashboard";
	public static final String PROFILE_PAGE = "/profile";

	private AuthenticationUtils() {
	}

	public static boolean isLoggedIn(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

	/** Nome exibido do usuário logado (usado como autor das considerações técnicas). */
	public static String displayName(Authentication authentication) {
		if (authentication == null) {
			return "Sistema";
		}
		if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user.getDisplayName();
		}
		return authentication.getName();
	}

	public static String homeFor(Authentication authentication) {
		boolean canSeeDashboard = authentication.getAuthorities().stream()
				.anyMatch(a -> Role.MANAGER.authority().equals(a.getAuthority())
						|| Role.ADMIN.authority().equals(a.getAuthority()));
		return canSeeDashboard ? DASHBOARD_PAGE : PROFILE_PAGE;
	}
}
