package br.com.danielcardoso.thindesk.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

	String message() default "A senha deve ter de 8 a 64 caracteres, com letra maiúscula, minúscula, número e símbolo";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
