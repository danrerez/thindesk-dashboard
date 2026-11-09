package br.com.danielcardoso.thindesk.common.validation;

import java.util.Objects;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, PasswordConfirmation> {

	@Override
	public boolean isValid(PasswordConfirmation form, ConstraintValidatorContext context) {
		if (form == null || Objects.equals(form.passwordToConfirm(), form.confirmation())) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode(form.confirmationField())
				.addConstraintViolation();
		return false;
	}
}
