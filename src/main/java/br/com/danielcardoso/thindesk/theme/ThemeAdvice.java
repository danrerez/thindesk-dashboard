package br.com.danielcardoso.thindesk.theme;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ThemeAdvice {

	private final ThemeService themeService;

	public ThemeAdvice(ThemeService themeService) {
		this.themeService = themeService;
	}

	@ModelAttribute("theme")
	public ThemeService theme() {
		return themeService;
	}
}
