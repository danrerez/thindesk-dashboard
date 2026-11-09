package br.com.danielcardoso.thindesk.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respostas de autenticação e autorização da API em JSON (Problem Details), em vez do redirecionamento para a tela
 * de login usado nas páginas. Não envia {@code WWW-Authenticate}, para o navegador não abrir a janela nativa de
 * usuário e senha quando a sessão do dashboard expira.
 */
public final class ApiSecurityHandlers {

	public static final String API_PREFIX = "/api/";

	/** Requisições para a API. */
	public static final RequestMatcher API = request -> pathOf(request).startsWith(API_PREFIX);

	/**
	 * Requisições para a API autenticadas por HTTP Basic (Postman, curl, integrações). Como o navegador não envia
	 * esse cabeçalho sozinho para outro site, elas não estão sujeitas a CSRF e podem dispensar o token.
	 */
	public static final RequestMatcher API_WITH_BASIC_AUTH = request -> API.matches(request)
			&& request.getHeader("Authorization") != null
			&& request.getHeader("Authorization").regionMatches(true, 0, "Basic ", 0, 6);

	private ApiSecurityHandlers() {
	}

	public static AuthenticationEntryPoint unauthorized() {
		return (request, response, exception) -> write(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
				"Não autenticado",
				"Faça login no dashboard ou envie credenciais válidas no cabeçalho Authorization (HTTP Basic).");
	}

	public static AccessDeniedHandler forbidden() {
		return (request, response, exception) -> {
			boolean csrf = exception.getClass().getSimpleName().contains("Csrf");
			write(response, request, HttpStatus.FORBIDDEN, csrf ? "CSRF_TOKEN_INVALID" : "FORBIDDEN",
					csrf ? "Token CSRF ausente ou inválido" : "Acesso negado",
					csrf ? "Envie o cabeçalho X-CSRF-TOKEN com o token da página ou autentique com HTTP Basic."
							: "Seu perfil não tem permissão para acessar este recurso.");
		};
	}

	private static void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
			String code, String title, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"/docs/errors#%s","title":"%s","status":%d,"detail":"%s","instance":"%s","code":"%s","timestamp":"%s"}"""
				.formatted(code.toLowerCase().replace('_', '-'), title, status.value(), detail,
						escape(request.getRequestURI()), code, Instant.now()));
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String pathOf(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}
}
