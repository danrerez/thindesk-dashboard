package br.com.danielcardoso.thindesk.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.danielcardoso.thindesk.user.dto.RegisterForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PasswordValidationTest {

	static ValidatorFactory factory;
	static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static RegisterForm form(String password, String confirmation) {
		RegisterForm form = new RegisterForm();
		form.setName("Maria");
		form.setEmail("maria@exemplo.com");
		form.setPassword(password);
		form.setConfirmPassword(confirmation);
		return form;
	}

	private static Set<String> invalidFields(RegisterForm form) {
		return validator.validate(form).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void acceptsStrongMatchingPassword() {
		assertThat(invalidFields(form("Senha@Forte1", "Senha@Forte1"))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "semmaiuscula@1", "SEMMINUSCULA@1", "SemNumero@!", "SemSimbolo123", "Aa@1" })
	void rejectsWeakPasswords(String password) {
		assertThat(invalidFields(form(password, password))).contains("password");
	}

	@Test
	void rejectsMismatchedConfirmation() {
		assertThat(invalidFields(form("Senha@Forte1", "Senha@Forte2"))).containsExactly("confirmPassword");
	}
}
