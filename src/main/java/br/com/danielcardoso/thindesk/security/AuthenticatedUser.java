package br.com.danielcardoso.thindesk.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;

public class AuthenticatedUser implements UserDetails, CredentialsContainer {

	private static final long serialVersionUID = 1L;

	private final String id;
	private final String displayName;
	private final String email;
	private final Role role;
	private final boolean enabled;
	private final boolean locked;
	private String password;

	public AuthenticatedUser(User user) {
		this.id = user.getId();
		this.displayName = user.getName();
		this.email = user.getEmail();
		this.role = user.getRole();
		this.enabled = user.isEnabled();
		this.locked = user.isLocked(Instant.now());
		this.password = user.getPassword();
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Role getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.authority()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !locked;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.password = null;
	}
}
