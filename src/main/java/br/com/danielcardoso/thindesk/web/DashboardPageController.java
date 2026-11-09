package br.com.danielcardoso.thindesk.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import br.com.danielcardoso.thindesk.config.AppProperties;

/**
 * Páginas do painel. Elas só montam a estrutura: todos os dados são carregados pelo navegador a partir da API REST
 * ({@code /api/v1/**}), o que mantém um único caminho de acesso aos dados.
 */
@Controller
@PreAuthorize("hasRole('MANAGER')")
public class DashboardPageController {

	private final AppProperties properties;

	public DashboardPageController(AppProperties properties) {
		this.properties = properties;
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("refreshSeconds", properties.dashboard().refreshSeconds());
		return "dashboard/index";
	}

	@GetMapping("/tickets")
	public String tickets() {
		return "tickets/list";
	}

	@GetMapping("/tickets/{id}")
	public String ticket(@PathVariable String id, Model model) {
		model.addAttribute("ticketId", id);
		return "tickets/detail";
	}
}
