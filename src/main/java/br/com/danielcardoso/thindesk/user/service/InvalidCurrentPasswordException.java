package br.com.danielcardoso.thindesk.user.service;

import br.com.danielcardoso.thindesk.common.exception.BusinessException;

public class InvalidCurrentPasswordException extends BusinessException {

	public InvalidCurrentPasswordException() {
		super("A senha atual está incorreta");
	}
}
