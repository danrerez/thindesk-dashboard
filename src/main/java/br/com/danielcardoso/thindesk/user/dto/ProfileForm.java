package br.com.danielcardoso.thindesk.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProfileForm {

	@NotBlank(message = "Informe seu nome")
	@Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres")
	private String name;

	public ProfileForm() {
	}

	public ProfileForm(String name) {
		setName(name);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? null : name.trim();
	}
}
