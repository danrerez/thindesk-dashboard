package br.com.danielcardoso.thindesk.user.dto;

import br.com.danielcardoso.thindesk.common.validation.PasswordConfirmation;
import br.com.danielcardoso.thindesk.common.validation.PasswordMatches;
import br.com.danielcardoso.thindesk.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordMatches
public class RegisterForm implements PasswordConfirmation {

	@NotBlank(message = "Informe seu nome")
	@Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres")
	private String name;

	@NotBlank(message = "Informe seu e-mail")
	@Email(message = "E-mail inválido")
	@Size(max = 120, message = "O e-mail deve ter no máximo 120 caracteres")
	private String email;

	@StrongPassword
	private String password;

	@NotBlank(message = "Confirme a senha")
	private String confirmPassword;

	@Override
	public String passwordToConfirm() {
		return password;
	}

	@Override
	public String confirmation() {
		return confirmPassword;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? null : name.trim();
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}
}
