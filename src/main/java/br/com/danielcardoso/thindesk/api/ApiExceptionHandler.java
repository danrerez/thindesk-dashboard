package br.com.danielcardoso.thindesk.api;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;
import br.com.danielcardoso.thindesk.common.exception.InvalidParameterException;
import br.com.danielcardoso.thindesk.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Converte as exceções da API em respostas no padrão Problem Details (RFC 9457), sempre com {@code code},
 * {@code timestamp} e, em erros de validação, a lista {@code errors} campo a campo. Vale só para os controllers
 * REST: as páginas Thymeleaf continuam usando as páginas de erro HTML.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
@Order(0)
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	public record FieldMessage(String field, String message) {
	}

	@ExceptionHandler(BindException.class)
	ResponseEntity<ProblemDetail> bind(BindException ex, HttpServletRequest request) {
		List<FieldMessage> errors = new ArrayList<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.add(fieldMessage(e)));
		ex.getBindingResult().getGlobalErrors()
				.forEach(e -> errors.add(new FieldMessage(e.getObjectName(), e.getDefaultMessage())));
		return validation(errors, request);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ProblemDetail> methodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
		List<FieldMessage> errors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String parameter = result.getMethodParameter().getParameterName();
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				if (error instanceof FieldError fieldError) {
					errors.add(fieldMessage(fieldError));
				}
				else {
					errors.add(new FieldMessage(parameter, error.getDefaultMessage()));
				}
			}
		});
		return validation(errors, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		String message = "Valor inválido '" + ex.getValue() + "'" + accepted(ex.getRequiredType());
		return validation(List.of(new FieldMessage(ex.getName(), message)), request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return validation(List.of(new FieldMessage(ex.getParameterName(), "Parâmetro obrigatório")), request);
	}

	@ExceptionHandler(InvalidParameterException.class)
	ResponseEntity<ProblemDetail> invalidParameter(InvalidParameterException ex, HttpServletRequest request) {
		return validation(List.of(new FieldMessage(ex.getParameter(), ex.getMessage())), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "MALFORMED_BODY", "Corpo da requisição inválido",
				"O JSON enviado está malformado ou contém valores inválidos (confira nomes de campos e enums).",
				request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado", ex.getMessage(), request);
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> business(BusinessException ex, HttpServletRequest request) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE", "Regra de negócio violada",
				ex.getMessage(), request);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> conflict(OptimisticLockingFailureException ex, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Atualização concorrente",
				"O chamado foi alterado por outra pessoa. Recarregue os dados e tente novamente.", request);
	}

	@ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
	ResponseEntity<ProblemDetail> forbidden(RuntimeException ex, HttpServletRequest request) {
		return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado",
				"Seu perfil não tem permissão para acessar este recurso.", request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ProblemDetail> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
		return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Tipo de conteúdo não suportado",
				"Envie o corpo como application/json.", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
		log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno",
				"Não foi possível processar a requisição. Tente novamente em instantes.", request);
	}

	private static ResponseEntity<ProblemDetail> validation(List<FieldMessage> errors, HttpServletRequest request) {
		ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
				"Parâmetros inválidos", errors.stream().map(FieldMessage::message).collect(Collectors.joining("; ")),
				request);
		response.getBody().setProperty("errors", errors);
		return response;
	}

	static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String title, String detail,
			HttpServletRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setTitle(title);
		body.setType(URI.create("/docs/errors#" + code.toLowerCase().replace('_', '-')));
		body.setInstance(URI.create(request.getRequestURI()));
		body.setProperty("code", code);
		body.setProperty("timestamp", Instant.now());
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

	private static FieldMessage fieldMessage(FieldError error) {
		if (error.isBindingFailure()) {
			return new FieldMessage(error.getField(), "Valor inválido '" + error.getRejectedValue() + "'"
					+ acceptedFor(error));
		}
		return new FieldMessage(error.getField(), error.getDefaultMessage());
	}

	private static String acceptedFor(FieldError error) {
		String field = error.getField().replaceAll("\\[\\d+]", "");
		Class<?> type = switch (field) {
			case "status" -> br.com.danielcardoso.thindesk.ticket.domain.TicketStatus.class;
			case "type" -> br.com.danielcardoso.thindesk.ticket.domain.TicketType.class;
			case "priority" -> br.com.danielcardoso.thindesk.ticket.domain.TicketPriority.class;
			case "department" -> br.com.danielcardoso.thindesk.ticket.domain.Department.class;
			case "from", "to" -> java.time.LocalDate.class;
			default -> null;
		};
		return accepted(type);
	}

	private static String accepted(Class<?> type) {
		if (type == null) {
			return "";
		}
		if (type.isEnum()) {
			return ". Valores aceitos: " + Arrays.stream(type.getEnumConstants()).map(Object::toString)
					.collect(Collectors.joining(", "));
		}
		if (type == java.time.LocalDate.class) {
			return ". Use o formato yyyy-MM-dd";
		}
		if (type == Integer.class || type == int.class) {
			return ". Informe um número inteiro";
		}
		if (type == Boolean.class || type == boolean.class) {
			return ". Use true ou false";
		}
		return "";
	}
}
