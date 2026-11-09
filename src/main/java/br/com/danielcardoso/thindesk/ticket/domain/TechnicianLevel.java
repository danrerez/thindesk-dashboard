package br.com.danielcardoso.thindesk.ticket.domain;

public enum TechnicianLevel {

	N1("Nível 1 (Service Desk)"),
	N2("Nível 2 (Suporte técnico)"),
	N3("Nível 3 (Especialista)");

	private final String label;

	TechnicianLevel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
