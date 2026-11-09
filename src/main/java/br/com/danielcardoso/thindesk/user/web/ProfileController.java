package br.com.danielcardoso.thindesk.user.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.danielcardoso.thindesk.security.AuthenticationRefresher;
import br.com.danielcardoso.thindesk.security.UserSessionInvalidator;
import br.com.danielcardoso.thindesk.user.domain.User;
import br.com.danielcardoso.thindesk.user.dto.ChangePasswordForm;
import br.com.danielcardoso.thindesk.user.dto.ProfileForm;
import br.com.danielcardoso.thindesk.user.dto.UserSummary;
import br.com.danielcardoso.thindesk.user.service.InvalidCurrentPasswordException;
import br.com.danielcardoso.thindesk.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/profile")
public class ProfileController {

	private final UserService userService;
	private final AuthenticationRefresher authenticationRefresher;
	private final UserSessionInvalidator sessionInvalidator;

	public ProfileController(UserService userService, AuthenticationRefresher authenticationRefresher,
			UserSessionInvalidator sessionInvalidator) {
		this.userService = userService;
		this.authenticationRefresher = authenticationRefresher;
		this.sessionInvalidator = sessionInvalidator;
	}

	@GetMapping
	public String profile(Authentication authentication, Model model) {
		User user = userService.getByEmail(authentication.getName());
		model.addAttribute("profileForm", new ProfileForm(user.getName()));
		model.addAttribute("passwordForm", new ChangePasswordForm());
		model.addAttribute("user", UserSummary.from(user));
		return "user/profile";
	}

	@PostMapping
	public String updateProfile(Authentication authentication,
			@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult result, Model model,
			HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return reloadPage(authentication, model, null, form);
		}
		User updated = userService.updateProfile(authentication.getName(), form);
		authenticationRefresher.refresh(updated, request, response);
		redirect.addFlashAttribute("successMessage", "Dados atualizados com sucesso.");
		return "redirect:/profile";
	}

	@PostMapping("/password")
	public String changePassword(Authentication authentication,
			@Valid @ModelAttribute("passwordForm") ChangePasswordForm form, BindingResult result, Model model,
			HttpServletRequest request, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				userService.changePassword(authentication.getName(), form);
				sessionInvalidator.invalidateOtherSessions(authentication.getName(), request.getSession().getId());
				redirect.addFlashAttribute("successMessage",
						"Senha alterada. As sessões abertas em outros dispositivos foram encerradas.");
				return "redirect:/profile";
			}
			catch (InvalidCurrentPasswordException ex) {
				result.rejectValue("currentPassword", "password.invalid", ex.getMessage());
			}
		}
		return reloadPage(authentication, model, form, null);
	}

	private String reloadPage(Authentication authentication, Model model, ChangePasswordForm passwordForm,
			ProfileForm profileForm) {
		User user = userService.getByEmail(authentication.getName());
		model.addAttribute("user", UserSummary.from(user));
		if (profileForm == null) {
			model.addAttribute("profileForm", new ProfileForm(user.getName()));
		}
		if (passwordForm == null) {
			model.addAttribute("passwordForm", new ChangePasswordForm());
		}
		return "user/profile";
	}
}
