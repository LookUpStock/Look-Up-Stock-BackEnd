package com.lookupstock.marktdaten.queue;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Zugriff auf die Queue-Tabelle public.queue_message (Migration V10 des core-service, ADR-07).
 * Die Queue ist die gemeinsame Infrastruktur beider Dienste; Fachtabellen des core-service liest dieser Dienst nicht.
 * Spalten und Regeln wie beim Verbraucher im core-service (UsageEventConsumer): älteste zuerst,
 * FOR UPDATE SKIP LOCKED, nach drei Fehlversuchen als fehlgeschlagen markiert.
 */
@Repository
public class QueueRepository {

    /** Eine offene Nachricht; payload ist der JSON-Inhalt als Text. */
    public record QueueEntry(long id, String payload) {
    }

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public QueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Sperrt die ältesten offenen Nachrichten eines Themas bis zum Ende der Transaktion. */
    public List<QueueEntry> lockNextBatch(String topic, int limit) {
        return jdbcTemplate.query("""
                SELECT id, payload::text AS payload
                FROM public.queue_message
                WHERE topic = ? AND failed = false
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new QueueEntry(rs.getLong("id"), rs.getString("payload")),
                topic, limit);
    }

    /** Erfolgreich verarbeitet: Nachricht löschen. */
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM public.queue_message WHERE id = ?", id);
    }

    /** Fehlversuch vermerken; ab maxAttempts wird die Nachricht nicht mehr abgeholt. */
    public void registerFailure(long id, String error, int maxAttempts) {
        String shortened = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        jdbcTemplate.update("""
                UPDATE public.queue_message
                SET attempts = attempts + 1, failed = (attempts + 1 >= ?), last_error = ?
                WHERE id = ?
                """, maxAttempts, shortened, id);
    }
}
