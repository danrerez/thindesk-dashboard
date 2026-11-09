package br.com.danielcardoso.thindesk.dashboard;

/**
 * Critério de ordenação do ranking de tipos de chamado.
 */
public enum RankingMetric {

	SOLVED("Chamados atendidos (resolvidos ou fechados)"),
	TOTAL("Chamados abertos no período");

	private final String label;

	RankingMetric(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
