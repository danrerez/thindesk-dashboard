package br.com.danielcardoso.thindesk.user.dto;

import java.time.Instant;

import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;

public record UserSummary(
		String id,
		String name,
		String email,
		Role role,
		boolean enabled,
		boolean locked,
		Instant createdAt,
		Instant lastLoginAt) {

	public static UserSummary from(User user) {
		return new UserSummary(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.isEnabled(),
				user.isLocked(Instant.now()),
				user.getCreatedAt(),
				user.getLastLoginAt());
	}
}
