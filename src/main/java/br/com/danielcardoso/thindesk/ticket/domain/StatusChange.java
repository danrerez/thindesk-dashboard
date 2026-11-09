package br.com.danielcardoso.thindesk.ticket.domain;

import java.time.Instant;

/**
 * Histórico de mudanças de situação do chamado (auditoria).
 */
public record StatusChange(TicketStatus from, TicketStatus to, String changedBy, Instant changedAt) {
}
