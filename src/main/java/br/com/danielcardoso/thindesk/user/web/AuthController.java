package br.com.danielcardoso.thindesk.user.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.danielcardoso.thindesk.security.AuthenticationUtils;
import br.com.danielcardoso.thindesk.user.dto.RegisterForm;
import br.com.danielcardoso.thindesk.user.service.EmailAlreadyUsedException;
import br.com.danielcardoso.thindesk.user.service.UserService;
import jakarta.validation.Valid;

@Controller
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/login")
	public String login(Authentication authentication) {
		return AuthenticationUtils.isLoggedIn(authentication)
				? "redirect:" + AuthenticationUtils.homeFor(authentication)
				: "auth/login";
	}

	@GetMapping("/register")
	public String registerForm(Authentication authentication, Model model) {
		if (AuthenticationUtils.isLoggedIn(authentication)) {
			return "redirect:" + AuthenticationUtils.homeFor(authentication);
		}
		model.addAttribute("form", new RegisterForm());
		return "auth/register";
	}

	@PostMapping("/register")
	public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result,
			RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return "auth/register";
		}
		try {
			userService.register(form);
		}
		catch (EmailAlreadyUsedException ex) {
			result.rejectValue("email", "email.used", ex.getMessage());
			return "auth/register";
		}
		redirect.addFlashAttribute("successMessage", "Cadastro realizado! Faça login para continuar.");
		return "redirect:/login";
	}
}
