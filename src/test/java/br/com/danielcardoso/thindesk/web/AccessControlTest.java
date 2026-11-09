package br.com.danielcardoso.thindesk.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mongodb.spring.session.MongoSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.danielcardoso.thindesk.admin.UserManagementController;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import br.com.danielcardoso.thindesk.config.AppConfig;
import br.com.danielcardoso.thindesk.config.SecurityConfig;
import br.com.danielcardoso.thindesk.security.AuthenticationRefresher;
import br.com.danielcardoso.thindesk.security.LoginAttemptService;
import br.com.danielcardoso.thindesk.security.LoginFailureHandler;
import br.com.danielcardoso.thindesk.security.LoginSuccessHandler;
import br.com.danielcardoso.thindesk.security.UserSessionInvalidator;
import br.com.danielcardoso.thindesk.theme.ThemeService;
import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.domain.User;
import br.com.danielcardoso.thindesk.user.dto.RegisterForm;
import br.com.danielcardoso.thindesk.user.dto.UserSummary;
import br.com.danielcardoso.thindesk.user.service.UserService;
import br.com.danielcardoso.thindesk.user.web.AuthController;
import br.com.danielcardoso.thindesk.user.web.ProfileController;

@WebMvcTest(controllers = { HomeController.class, AuthController.class, ProfileController.class,
		UserManagementController.class })
@Import({ SecurityConfig.class, AppConfig.class, ThemeService.class, LoginSuccessHandler.class,
		LoginFailureHandler.class, AuthenticationRefresher.class, UserSessionInvalidator.class })
class AccessControlTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserService userService;

	@MockitoBean
	LoginAttemptService loginAttemptService;

	@MockitoBean
	FindByIndexNameSessionRepository<MongoSession> sessionRepository;

	private void givenLoggedUser(Role role) {
		User user = new User("Fulano de Tal", "fulano@exemplo.com", "{bcrypt}hash", role);
		user.setId("1");
		user.setCreatedAt(Instant.now());
		when(userService.getByEmail(anyString())).thenReturn(user);
		when(userService.countByRole()).thenReturn(Map.of(Role.USER, 3L, Role.MANAGER, 1L, Role.ADMIN, 1L));
		User other = new User("Outra Pessoa", "outra@exemplo.com", "{bcrypt}hash", Role.USER);
		other.setId("2");
		when(userService.listAll()).thenReturn(List.of(UserSummary.from(user), UserSummary.from(other)));
	}

	@Test
	void publicPagesAreAccessibleWithoutLogin() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Entrar")));
		mvc.perform(get("/register")).andExpect(status().isOk());
	}

	@Test
	void protectedPagesRedirectAnonymousToLogin() throws Exception {
		mvc.perform(get("/profile")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		mvc.perform(get("/users")).andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "USER")
	void userAccessesOnlyOwnArea() throws Exception {
		givenLoggedUser(Role.USER);
		mvc.perform(get("/profile")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Fulano de Tal")))
				.andExpect(content().string(not(containsString("href=\"/users\""))));
		mvc.perform(get("/users")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "USER")
	void loggedUserGoesToProfileAsHome() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/profile"));
		mvc.perform(get("/login")).andExpect(redirectedUrl("/profile"));
	}

	@Test
	@WithMockUser(username = "admin@exemplo.com", roles = "ADMIN")
	void managersAndAdminsGoToDashboardAsHome() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/dashboard"));
		mvc.perform(get("/register")).andExpect(redirectedUrl("/dashboard"));
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "MANAGER")
	void managerSeesUsersReadOnly() throws Exception {
		givenLoggedUser(Role.MANAGER);
		mvc.perform(get("/users")).andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/users\"")))
				.andExpect(content().string(containsString("Outra Pessoa")))
				.andExpect(content().string(not(containsString("/users/2/role"))))
				.andExpect(content().string(not(containsString("/users/2/delete"))));
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "MANAGER")
	void managerCannotCallAdminActions() throws Exception {
		mvc.perform(post("/users/2/role").param("role", "ADMIN").with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(post("/users/2/delete").with(csrf())).andExpect(status().isForbidden());
		verify(userService, never()).changeRole(anyString(), anyString(), any());
		verify(userService, never()).delete(anyString(), anyString());
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "ADMIN")
	void adminSeesSameScreenWithActions() throws Exception {
		givenLoggedUser(Role.ADMIN);
		mvc.perform(get("/users")).andExpect(status().isOk())
				.andExpect(content().string(containsString("/users/2/role")))
				.andExpect(content().string(containsString("/users/2/delete")))
				.andExpect(content().string(not(containsString("/users/1/delete"))));
	}

	@Test
	@WithMockUser(username = "admin@exemplo.com", roles = "ADMIN")
	void adminCanChangeRoles() throws Exception {
		mvc.perform(post("/users/2/role").param("role", "MANAGER").with(csrf()))
				.andExpect(redirectedUrl("/users"));
		verify(userService).changeRole("admin@exemplo.com", "2", Role.MANAGER);
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "USER")
	void userCannotCallAdminActions() throws Exception {
		mvc.perform(post("/users/2/role").param("role", "ADMIN").with(csrf()))
				.andExpect(status().isForbidden());
		verify(userService, never()).changeRole(anyString(), anyString(), any());
	}

	@Test
	@WithMockUser(username = "admin@exemplo.com", roles = "ADMIN")
	void unknownUserReturnsNotFound() throws Exception {
		doThrow(new ResourceNotFoundException("Usuário não encontrado"))
				.when(userService).toggleEnabled("admin@exemplo.com", "999");
		mvc.perform(post("/users/999/toggle").with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "fulano@exemplo.com", roles = "USER")
	void changingPasswordEndsOtherSessions() throws Exception {
		mvc.perform(post("/profile/password").with(csrf())
				.param("currentPassword", "Senha@Atual1")
				.param("newPassword", "Nova@Senha1")
				.param("confirmPassword", "Nova@Senha1"))
				.andExpect(redirectedUrl("/profile"));
		verify(sessionRepository).findByPrincipalName("fulano@exemplo.com");
	}

	@Test
	@WithMockUser
	void postsWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/logout")).andExpect(status().isForbidden());
		mvc.perform(post("/logout").with(csrf())).andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	void registerRejectsWeakPasswordAndMismatch() throws Exception {
		mvc.perform(post("/register").with(csrf())
				.param("name", "Fulano")
				.param("email", "fulano@exemplo.com")
				.param("password", "123")
				.param("confirmPassword", "456"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "password", "confirmPassword"));
		verify(userService, never()).register(any());
	}

	@Test
	void registerValidUserRedirectsToLogin() throws Exception {
		mvc.perform(post("/register").with(csrf())
				.param("name", "Fulano")
				.param("email", "fulano@exemplo.com")
				.param("password", "Senha@Forte1")
				.param("confirmPassword", "Senha@Forte1"))
				.andExpect(redirectedUrl("/login"));
		verify(userService).register(any(RegisterForm.class));
	}

	@Test
	void securityHeadersArePresent() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}
}
