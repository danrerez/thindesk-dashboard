package br.com.danielcardoso.thindesk.api;

import static br.com.danielcardoso.thindesk.ticket.TicketFixtures.NOW;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mongodb.spring.session.MongoSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import br.com.danielcardoso.thindesk.config.AppConfig;
import br.com.danielcardoso.thindesk.config.SecurityConfig;
import br.com.danielcardoso.thindesk.dashboard.DashboardMetrics;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Period;
import br.com.danielcardoso.thindesk.dashboard.DashboardResponses.Summary;
import br.com.danielcardoso.thindesk.dashboard.DashboardService;
import br.com.danielcardoso.thindesk.dashboard.Granularity;
import br.com.danielcardoso.thindesk.dashboard.RankingMetric;
import br.com.danielcardoso.thindesk.security.AuthenticationRefresher;
import br.com.danielcardoso.thindesk.security.LoginAttemptService;
import br.com.danielcardoso.thindesk.security.LoginFailureHandler;
import br.com.danielcardoso.thindesk.security.LoginSuccessHandler;
import br.com.danielcardoso.thindesk.security.UserSessionInvalidator;
import br.com.danielcardoso.thindesk.theme.ThemeService;
import br.com.danielcardoso.thindesk.ticket.TicketFixtures;
import br.com.danielcardoso.thindesk.ticket.domain.Technician;
import br.com.danielcardoso.thindesk.ticket.domain.Ticket;
import br.com.danielcardoso.thindesk.ticket.domain.TicketPriority;
import br.com.danielcardoso.thindesk.ticket.domain.TicketType;
import br.com.danielcardoso.thindesk.ticket.dto.TicketRequests;
import br.com.danielcardoso.thindesk.ticket.dto.TicketSearchParams;
import br.com.danielcardoso.thindesk.ticket.service.TicketService;
import br.com.danielcardoso.thindesk.web.DashboardPageController;

/**
 * Testes da camada web da API com o Spring Security real: autenticação, autorização por perfil, CSRF, validação
 * dos parâmetros e formato padronizado dos erros. Os serviços são simulados (não depende do Atlas).
 */
@WebMvcTest(controllers = { DashboardApiController.class, TicketApiController.class, DashboardPageController.class })
@Import({ SecurityConfig.class, AppConfig.class, ThemeService.class, LoginSuccessHandler.class,
		LoginFailureHandler.class, AuthenticationRefresher.class, UserSessionInvalidator.class })
class DashboardApiTest {

	static final String SUMMARY = "/api/v1/dashboard/summary";
	static final String BASIC_USER = "gestor@exemplo.com";
	static final String BASIC_PASSWORD = "Senha@123";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	DashboardService dashboard;

	@MockitoBean
	TicketService tickets;

	@MockitoBean
	LoginAttemptService loginAttemptService;

	@MockitoBean
	UserDetailsService userDetailsService;

	@MockitoBean
	FindByIndexNameSessionRepository<MongoSession> sessionRepository;

	Technician ana = TicketFixtures.technician("ana", "Ana");

	@BeforeEach
	void setUp() {
		when(dashboard.zone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
		when(tickets.now()).thenReturn(NOW);
		when(dashboard.summary(any())).thenAnswer(i -> {
			List<Ticket> list = List.of(TicketFixtures.open(TicketType.ERP, TicketPriority.HIGH, NOW));
			TicketSearchParams p = i.getArgument(0);
			return new Summary(new Period(p.from(), p.to()), DashboardMetrics.totals(list, NOW),
					DashboardMetrics.rates(list), DashboardMetrics.times(list), null, NOW);
		});
		when(userDetailsService.loadUserByUsername(BASIC_USER)).thenReturn(User.withUsername(BASIC_USER)
				.password("{bcrypt}" + new BCryptPasswordEncoder(4).encode(BASIC_PASSWORD))
				.roles("MANAGER").build());
	}

	@Test
	@DisplayName("401 em JSON (sem redirecionar e sem janela de login do navegador) para quem não está logado")
	void anonymousGets401Json() throws Exception {
		mvc.perform(get(SUMMARY))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(header().doesNotExist("WWW-Authenticate"));
	}

	@Test
	@DisplayName("Credenciais HTTP Basic erradas também geram 401")
	void wrongBasicCredentials() throws Exception {
		mvc.perform(get(SUMMARY).with(httpBasic(BASIC_USER, "errada")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(roles = "USER")
	@DisplayName("Perfil USER não acessa a API nem o dashboard (403)")
	void userRoleIsForbidden() throws Exception {
		mvc.perform(get(SUMMARY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Visitante é levado ao login ao abrir o dashboard")
	void anonymousPageRedirectsToLogin() throws Exception {
		mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF01/RF06: gerente consulta os indicadores com filtros")
	void managerGetsSummary() throws Exception {
		mvc.perform(get(SUMMARY).param("from", "2026-10-01").param("to", "2026-10-31")
				.param("status", "OPEN,IN_PROGRESS").param("type", "ERP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period.from").value("2026-10-01"))
				.andExpect(jsonPath("$.totals.total").value(1))
				.andExpect(jsonPath("$.totals.active").value(1))
				.andExpect(jsonPath("$.rates.slaMetPercent").doesNotExist());
	}

	@Test
	@DisplayName("RF06: HTTP Basic autentica a API (Postman, integrações)")
	void basicAuthWorks() throws Exception {
		mvc.perform(get(SUMMARY).with(httpBasic(BASIC_USER, BASIC_PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF05: data inicial depois da final é recusada com 400")
	void invalidPeriod() throws Exception {
		mvc.perform(get(SUMMARY).param("from", "2026-11-09").param("to", "2026-10-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("periodOrdered"));
		verify(dashboard, never()).summary(any());
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF05: valores de filtro inválidos informam os valores aceitos")
	void invalidFilterValues() throws Exception {
		mvc.perform(get(SUMMARY).param("status", "ABERTO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("status"))
				.andExpect(jsonPath("$.errors[0].message").value(containsString("OPEN, IN_PROGRESS")));
		mvc.perform(get(SUMMARY).param("from", "09/11/2026"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value(containsString("yyyy-MM-dd")));
		mvc.perform(get("/api/v1/dashboard/types").param("limit", "99"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("limit"));
		mvc.perform(get("/api/v1/dashboard/timeline").param("granularity", "YEAR"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("granularity"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF02/RF04: parâmetros específicos chegam ao serviço")
	void forwardsSpecificParameters() throws Exception {
		mvc.perform(get("/api/v1/dashboard/timeline").param("granularity", "WEEK")).andExpect(status().isOk());
		verify(dashboard).timeline(any(), eq(Granularity.WEEK));
		mvc.perform(get("/api/v1/dashboard/types").param("metric", "TOTAL").param("limit", "3"))
				.andExpect(status().isOk());
		verify(dashboard).types(any(), eq(RankingMetric.TOTAL), eq(3));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("Listagem paginada recusa ordenação por campo não permitido")
	void listValidatesSort() throws Exception {
		when(tickets.search(any(), any())).thenReturn(new PageImpl<>(
				List.of(TicketFixtures.open(TicketType.EMAIL, TicketPriority.LOW, NOW)), PageRequest.of(0, 20), 1));

		mvc.perform(get("/api/v1/tickets").param("sort", "dueAt,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status.code").value("OPEN"));
		mvc.perform(get("/api/v1/tickets").param("sort", "password,asc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("sort"));
		mvc.perform(get("/api/v1/tickets").param("size", "500"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("404 para chamado inexistente")
	void ticketNotFound() throws Exception {
		when(tickets.get("x")).thenThrow(new ResourceNotFoundException("Chamado x não encontrado."));

		mvc.perform(get("/api/v1/tickets/x"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.instance").value("/api/v1/tickets/x"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF07: alteração com sessão exige token CSRF")
	void sessionWritesRequireCsrf() throws Exception {
		mvc.perform(patch("/api/v1/tickets/x/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF07: regra de negócio violada retorna 422")
	void businessRuleReturns422() throws Exception {
		when(tickets.changeStatus(eq("x"), any(), anyString()))
				.thenThrow(new BusinessException("Atribua um técnico antes de iniciar o atendimento."));

		mvc.perform(patch("/api/v1/tickets/x/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("BUSINESS_RULE"))
				.andExpect(jsonPath("$.detail").value(containsString("técnico")));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("RF07: corpo inválido lista os erros por campo")
	void createValidation() throws Exception {
		mvc.perform(post("/api/v1/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"abc\",\"requesterEmail\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("requesterEmail")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("type")));
		mvc.perform(post("/api/v1/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"FOO\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
	}

	@Test
	@DisplayName("RF07: criação via HTTP Basic dispensa CSRF e devolve 201 com Location")
	void createWithBasicAuth() throws Exception {
		Ticket created = TicketFixtures.open(TicketType.NETWORK, TicketPriority.HIGH, NOW);
		when(tickets.create(any(TicketRequests.CreateTicket.class), anyString())).thenReturn(created);

		mvc.perform(post("/api/v1/tickets").with(httpBasic(BASIC_USER, BASIC_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Sem internet","description":"Rede caiu","type":"NETWORK","priority":"HIGH",
						 "requesterName":"Maria","requesterEmail":"maria@exemplo.com","department":"SALES"}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/tickets/" + created.getId())))
				.andExpect(jsonPath("$.protocol").value(created.getProtocol()))
				.andExpect(jsonPath("$.allowedTransitions[0].code").value("IN_PROGRESS"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("Detalhe traz considerações técnicas, histórico e próximas situações")
	void ticketDetail() throws Exception {
		Ticket ticket = TicketFixtures.resolved(TicketType.HARDWARE, TicketPriority.HIGH, NOW, ana,
				Duration.ofHours(1), Duration.ofHours(3));
		when(tickets.get(ticket.getId())).thenReturn(ticket);

		mvc.perform(get("/api/v1/tickets/" + ticket.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status.code").value("RESOLVED"))
				.andExpect(jsonPath("$.slaMet").value(true))
				.andExpect(jsonPath("$.notes[0].text").value("Solução"))
				.andExpect(jsonPath("$.history.length()").value(2))
				.andExpect(jsonPath("$.allowedTransitions[*].code", hasItem("CLOSED")));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("Rota inexistente e método não suportado também respondem em Problem Details")
	void routingErrorsAreJson() throws Exception {
		mvc.perform(get("/api/v1/nao-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(delete("/api/v1/tickets/x").with(csrf()))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	@DisplayName("Página do dashboard carrega para o gerente")
	void dashboardPage() throws Exception {
		mvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Dashboard de chamados")))
				.andExpect(content().string(containsString("/webjars/chart.js/")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
	}
}
