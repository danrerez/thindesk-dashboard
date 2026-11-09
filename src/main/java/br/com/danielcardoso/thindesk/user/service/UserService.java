package br.com.danielcardoso.thindesk.user.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;
import br.com.danielcardoso.thindesk.user.dto.ChangePasswordForm;
import br.com.danielcardoso.thindesk.user.dto.ProfileForm;
import br.com.danielcardoso.thindesk.user.dto.RegisterForm;
import br.com.danielcardoso.thindesk.user.dto.UserSummary;
import br.com.danielcardoso.thindesk.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher events;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	public User register(RegisterForm form) {
		return create(form.getName(), form.getEmail(), form.getPassword(), Role.USER);
	}

	public boolean createAdminIfMissing(String name, String email, String rawPassword) {
		if (repository.existsByEmail(normalizeEmail(email))) {
			return false;
		}
		create(name, email, rawPassword, Role.ADMIN);
		return true;
	}

	private User create(String name, String email, String rawPassword, Role role) {
		String normalizedEmail = normalizeEmail(email);
		if (repository.existsByEmail(normalizedEmail)) {
			throw new EmailAlreadyUsedException();
		}
		User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword), role);
		try {
			return repository.save(user);
		}
		catch (DuplicateKeyException ex) {
			throw new EmailAlreadyUsedException();
		}
	}

	public User getByEmail(String email) {
		return repository.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
	}

	public User getById(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
	}

	public List<UserSummary> listAll() {
		return repository.findAllByOrderByCreatedAtDesc().stream().map(UserSummary::from).toList();
	}

	public Map<Role, Long> countByRole() {
		Map<Role, Long> counts = new EnumMap<>(Role.class);
		for (Role role : Role.values()) {
			counts.put(role, repository.countByRole(role));
		}
		return counts;
	}

	public User updateProfile(String email, ProfileForm form) {
		User user = getByEmail(email);
		user.setName(form.getName().trim());
		return repository.save(user);
	}

	public void changePassword(String email, ChangePasswordForm form) {
		User user = getByEmail(email);
		if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
			throw new InvalidCurrentPasswordException();
		}
		user.setPassword(passwordEncoder.encode(form.getNewPassword()));
		repository.save(user);
	}

	public void changeRole(String actorEmail, String targetId, Role role) {
		User target = getManageableUser(actorEmail, targetId, "alterar o próprio perfil");
		if (target.getRole() != role) {
			target.setRole(role);
			repository.save(target);
			events.publishEvent(new UserAccessChangedEvent(target.getEmail()));
		}
	}

	public void toggleEnabled(String actorEmail, String targetId) {
		User target = getManageableUser(actorEmail, targetId, "desativar a própria conta");
		target.setEnabled(!target.isEnabled());
		repository.save(target);
		if (!target.isEnabled()) {
			events.publishEvent(new UserAccessChangedEvent(target.getEmail()));
		}
	}

	public void unlock(String targetId) {
		User target = getById(targetId);
		target.setFailedLoginAttempts(0);
		target.setLockedUntil(null);
		repository.save(target);
	}

	public void delete(String actorEmail, String targetId) {
		User target = getManageableUser(actorEmail, targetId, "excluir a própria conta");
		repository.delete(target);
		events.publishEvent(new UserAccessChangedEvent(target.getEmail()));
	}

	private User getManageableUser(String actorEmail, String targetId, String forbiddenAction) {
		User target = getById(targetId);
		if (target.getEmail().equals(normalizeEmail(actorEmail))) {
			throw new BusinessException("Você não pode " + forbiddenAction);
		}
		return target;
	}
}
