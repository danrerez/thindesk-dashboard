package br.com.danielcardoso.thindesk.ticket.domain;

/**
 * Tipo (categoria) do chamado técnico.
 */
public enum TicketType {

	HARDWARE("Hardware"),
	SOFTWARE("Software e instalação"),
	NETWORK("Rede e internet"),
	ACCESS("Acesso e senhas"),
	EMAIL("E-mail"),
	PRINTER("Impressoras"),
	ERP("Sistema ERP"),
	PHONE("Telefonia");

	private final String label;

	TicketType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
