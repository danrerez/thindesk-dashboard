package br.com.danielcardoso.thindesk.common.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

	static final int MIN_LENGTH = 8;
	static final int MAX_LENGTH = 64;
	static final int BCRYPT_MAX_BYTES = 72;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return false;
		}
		return value.length() >= MIN_LENGTH
				&& value.length() <= MAX_LENGTH
				&& value.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
				&& value.chars().anyMatch(Character::isUpperCase)
				&& value.chars().anyMatch(Character::isLowerCase)
				&& value.chars().anyMatch(Character::isDigit)
				&& value.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
	}
}
