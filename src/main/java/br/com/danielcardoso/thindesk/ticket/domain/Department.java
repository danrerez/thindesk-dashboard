package br.com.danielcardoso.thindesk.ticket.domain;

/**
 * Setor de quem abriu o chamado.
 */
public enum Department {

	FINANCE("Financeiro"),
	HR("Recursos Humanos"),
	SALES("Comercial"),
	OPERATIONS("Operações"),
	MARKETING("Marketing"),
	LOGISTICS("Logística"),
	LEGAL("Jurídico"),
	BOARD("Diretoria");

	private final String label;

	Department(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
