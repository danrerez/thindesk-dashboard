package br.com.danielcardoso.thindesk.common.exception;

/**
 * Parâmetro de consulta com valor válido no formato, mas inválido para a regra (ex.: período longo demais para a
 * granularidade pedida). Vira HTTP 400 na API.
 */
public class InvalidParameterException extends RuntimeException {

	private final String parameter;

	public InvalidParameterException(String parameter, String message) {
		super(message);
		this.parameter = parameter;
	}

	public String getParameter() {
		return parameter;
	}
}
