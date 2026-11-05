package com.lookupstock.marktdaten.queue;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QueueRepository {

    public record QueueEntry(long id, String payload) {
    }

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public QueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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

    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM public.queue_message WHERE id = ?", id);
    }

    public void registerFailure(long id, String error, int maxAttempts) {
        String shortened = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        jdbcTemplate.update("""
                UPDATE public.queue_message
                SET attempts = attempts + 1, failed = (attempts + 1 >= ?), last_error = ?
                WHERE id = ?
                """, maxAttempts, shortened, id);
    }
}
