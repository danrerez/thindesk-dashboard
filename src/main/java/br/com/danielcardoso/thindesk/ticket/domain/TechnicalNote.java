package br.com.danielcardoso.thindesk.ticket.domain;

import java.time.Instant;

/**
 * Consideração técnica registrada no chamado (diagnóstico, andamento ou solução). Fica embutida no documento do
 * chamado porque só faz sentido dentro dele e é sempre lida junto.
 */
public record TechnicalNote(String author, String text, Instant createdAt) {
}
