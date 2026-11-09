package br.com.danielcardoso.thindesk.common.validation;

public interface PasswordConfirmation {

	String passwordToConfirm();

	String confirmation();

	default String confirmationField() {
		return "confirmPassword";
	}
}
