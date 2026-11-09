package br.com.danielcardoso.thindesk.user.dto;

import br.com.danielcardoso.thindesk.common.validation.PasswordConfirmation;
import br.com.danielcardoso.thindesk.common.validation.PasswordMatches;
import br.com.danielcardoso.thindesk.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

@PasswordMatches
public class ChangePasswordForm implements PasswordConfirmation {

	@NotBlank(message = "Informe a senha atual")
	private String currentPassword;

	@StrongPassword
	private String newPassword;

	@NotBlank(message = "Confirme a nova senha")
	private String confirmPassword;

	@Override
	public String passwordToConfirm() {
		return newPassword;
	}

	@Override
	public String confirmation() {
		return confirmPassword;
	}

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}
}
