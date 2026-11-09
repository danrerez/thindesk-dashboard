package br.com.danielcardoso.thindesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;
import br.com.danielcardoso.thindesk.user.dto.ChangePasswordForm;
import br.com.danielcardoso.thindesk.user.dto.RegisterForm;
import br.com.danielcardoso.thindesk.user.repository.UserRepository;
import br.com.danielcardoso.thindesk.user.service.EmailAlreadyUsedException;
import br.com.danielcardoso.thindesk.user.service.InvalidCurrentPasswordException;
import br.com.danielcardoso.thindesk.user.service.UserAccessChangedEvent;
import br.com.danielcardoso.thindesk.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	UserRepository repository;

	@Mock
	ApplicationEventPublisher events;

	PasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4)));

	UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(repository, encoder, events);
	}

	private static RegisterForm form(String email, String password) {
		RegisterForm form = new RegisterForm();
		form.setName("  Maria Silva ");
		form.setEmail(email);
		form.setPassword(password);
		form.setConfirmPassword(password);
		return form;
	}

	@Test
	void registerHashesPasswordNormalizesEmailAndAssignsUserRole() {
		when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		User saved = service.register(form("  Maria@Exemplo.COM ", "Senha@Forte1"));

		assertThat(saved.getEmail()).isEqualTo("maria@exemplo.com");
		assertThat(saved.getName()).isEqualTo("Maria Silva");
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getPassword()).startsWith("{bcrypt}$2a$").isNotEqualTo("Senha@Forte1");
		assertThat(encoder.matches("Senha@Forte1", saved.getPassword())).isTrue();
	}

	@Test
	void registerRejectsDuplicatedEmail() {
		when(repository.existsByEmail("maria@exemplo.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(form("maria@exemplo.com", "Senha@Forte1")))
				.isInstanceOf(EmailAlreadyUsedException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void changePasswordRequiresCurrentPassword() {
		User user = new User("Maria", "maria@exemplo.com", encoder.encode("Senha@Atual1"), Role.USER);
		when(repository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(user));
		ChangePasswordForm form = new ChangePasswordForm();
		form.setCurrentPassword("errada");
		form.setNewPassword("Nova@Senha1");
		form.setConfirmPassword("Nova@Senha1");

		assertThatThrownBy(() -> service.changePassword("maria@exemplo.com", form))
				.isInstanceOf(InvalidCurrentPasswordException.class);

		form.setCurrentPassword("Senha@Atual1");
		service.changePassword("maria@exemplo.com", form);
		assertThat(encoder.matches("Nova@Senha1", user.getPassword())).isTrue();
	}

	@Test
	void adminCannotChangeOwnRole() {
		User admin = new User("Admin", "admin@exemplo.com", "x", Role.ADMIN);
		admin.setId("1");
		when(repository.findById("1")).thenReturn(Optional.of(admin));

		assertThatThrownBy(() -> service.changeRole("admin@exemplo.com", "1", Role.USER))
				.isInstanceOf(BusinessException.class);
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void changingRoleEndsTargetSessions() {
		User user = new User("Maria", "maria@exemplo.com", "x", Role.USER);
		user.setId("2");
		when(repository.findById("2")).thenReturn(Optional.of(user));

		service.changeRole("admin@exemplo.com", "2", Role.MANAGER);

		assertThat(user.getRole()).isEqualTo(Role.MANAGER);
		ArgumentCaptor<UserAccessChangedEvent> event = ArgumentCaptor.forClass(UserAccessChangedEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().email()).isEqualTo("maria@exemplo.com");
	}
}
