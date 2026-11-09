package br.com.danielcardoso.thindesk.user.service;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;

public class EmailAlreadyUsedException extends BusinessException {

	public EmailAlreadyUsedException() {
		super("Este e-mail já está cadastrado");
	}
}
