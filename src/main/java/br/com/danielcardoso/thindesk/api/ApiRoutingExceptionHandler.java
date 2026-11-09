package br.com.danielcardoso.thindesk.api;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.danielcardoso.thindesk.security.ApiSecurityHandlers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Erros que acontecem antes de existir um controller (rota inexistente, método HTTP não aceito). Em
 * {@code /api/**} viram Problem Details como o resto da API; nas páginas seguem para a página de erro HTML.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiRoutingExceptionHandler {

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ProblemDetail> notFound(NoResourceFoundException ex, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		if (!ApiSecurityHandlers.API.matches(request)) {
			response.sendError(HttpStatus.NOT_FOUND.value());
			return null;
		}
		return ApiExceptionHandler.problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado",
				"Não existe endpoint em " + request.getRequestURI() + ". Consulte /swagger-ui.html.", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!ApiSecurityHandlers.API.matches(request)) {
			response.sendError(HttpStatus.METHOD_NOT_ALLOWED.value());
			return null;
		}
		ResponseEntity<ProblemDetail> problem = ApiExceptionHandler.problem(HttpStatus.METHOD_NOT_ALLOWED,
				"METHOD_NOT_ALLOWED", "Método não suportado",
				"O método " + ex.getMethod() + " não é aceito neste endpoint.", request);
		return ResponseEntity.status(problem.getStatusCode()).headers(problem.getHeaders())
				.allow(ex.getSupportedHttpMethods() == null ? new org.springframework.http.HttpMethod[0]
						: ex.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new))
				.body(problem.getBody());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ProblemDetail> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		if (!ApiSecurityHandlers.API.matches(request)) {
			response.sendError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
			return null;
		}
		return ApiExceptionHandler.problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
				"Tipo de conteúdo não suportado", "Envie o corpo como application/json.", request);
	}
}
