package br.com.danielcardoso.thindesk.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.user.domain.Role;
import br.com.danielcardoso.thindesk.user.service.UserService;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('MANAGER')")
public class UserManagementController {

	private static final String REDIRECT = "redirect:/users";

	private final UserService userService;

	public UserManagementController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public String users(Authentication authentication, Model model) {
		model.addAttribute("users", userService.listAll());
		model.addAttribute("roleCounts", userService.countByRole());
		model.addAttribute("roles", Role.values());
		model.addAttribute("canManage", authentication.getAuthorities().stream()
				.anyMatch(a -> Role.ADMIN.authority().equals(a.getAuthority())));
		return "users/list";
	}

	@PostMapping("/{id}/role")
	@PreAuthorize("hasRole('ADMIN')")
	public String changeRole(@PathVariable String id, @RequestParam Role role, Authentication authentication,
			RedirectAttributes redirect) {
		return execute(redirect, "Perfil atualizado. As sessões do usuário foram encerradas.",
				() -> userService.changeRole(authentication.getName(), id, role));
	}

	@PostMapping("/{id}/toggle")
	@PreAuthorize("hasRole('ADMIN')")
	public String toggle(@PathVariable String id, Authentication authentication, RedirectAttributes redirect) {
		return execute(redirect, "Status da conta atualizado.",
				() -> userService.toggleEnabled(authentication.getName(), id));
	}

	@PostMapping("/{id}/unlock")
	@PreAuthorize("hasRole('ADMIN')")
	public String unlock(@PathVariable String id, RedirectAttributes redirect) {
		return execute(redirect, "Conta desbloqueada.", () -> userService.unlock(id));
	}

	@PostMapping("/{id}/delete")
	@PreAuthorize("hasRole('ADMIN')")
	public String delete(@PathVariable String id, Authentication authentication, RedirectAttributes redirect) {
		return execute(redirect, "Usuário excluído.", () -> userService.delete(authentication.getName(), id));
	}

	private String execute(RedirectAttributes redirect, String successMessage, Runnable action) {
		try {
			action.run();
			redirect.addFlashAttribute("successMessage", successMessage);
		}
		catch (BusinessException ex) {
			redirect.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return REDIRECT;
	}
}
