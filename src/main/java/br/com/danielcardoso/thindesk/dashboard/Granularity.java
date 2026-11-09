package br.com.danielcardoso.thindesk.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Agrupamento da série histórica. Sem granularidade informada, a API escolhe pelo tamanho do período.
 */
public enum Granularity {

	DAY("Dia"),
	WEEK("Semana"),
	MONTH("Mês");

	static final int AUTO_DAY_LIMIT = 45;
	static final int AUTO_WEEK_LIMIT = 200;
	static final int MAX_POINTS = 400;

	private static final Locale PT_BR = Locale.of("pt", "BR");
	private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
	private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM/yy", PT_BR);

	private final String label;

	Granularity(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static Granularity auto(long days) {
		if (days <= AUTO_DAY_LIMIT) {
			return DAY;
		}
		return days <= AUTO_WEEK_LIMIT ? WEEK : MONTH;
	}

	/** Primeiro dia do intervalo que contém a data (segunda-feira para semanas, dia 1 para meses). */
	public LocalDate bucketOf(LocalDate date) {
		return switch (this) {
			case DAY -> date;
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTH -> date.withDayOfMonth(1);
		};
	}

	public LocalDate next(LocalDate bucket) {
		return switch (this) {
			case DAY -> bucket.plusDays(1);
			case WEEK -> bucket.plusWeeks(1);
			case MONTH -> bucket.plusMonths(1);
		};
	}

	public String labelOf(LocalDate bucket) {
		return switch (this) {
			case DAY -> DAY_LABEL.format(bucket);
			case WEEK -> "Sem. " + DAY_LABEL.format(bucket);
			case MONTH -> MONTH_LABEL.format(bucket).replace(".", "");
		};
	}
}
