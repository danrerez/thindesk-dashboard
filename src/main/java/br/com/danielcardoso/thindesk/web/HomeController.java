package br.com.danielcardoso.thindesk.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.danielcardoso.thindesk.security.AuthenticationUtils;

@Controller
public class HomeController {

	@GetMapping("/")
	public String index(Authentication authentication) {
		return AuthenticationUtils.isLoggedIn(authentication)
				? "redirect:" + AuthenticationUtils.homeFor(authentication)
				: "redirect:/login";
	}
}
