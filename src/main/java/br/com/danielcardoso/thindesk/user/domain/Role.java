package br.com.danielcardoso.thindesk.user.domain;

public enum Role {

	USER("Usuário"),
	MANAGER("Gerente"),
	ADMIN("Administrador");

	public static final String PREFIX = "ROLE_";

	private final String label;

	Role(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public String authority() {
		return PREFIX + name();
	}
}
