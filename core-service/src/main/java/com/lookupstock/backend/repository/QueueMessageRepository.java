package com.lookupstock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lookupstock.backend.model.QueueMessage;

/**
 * Zugriff auf die Queue-Tabelle (BE #114, ADR-07).
 */
public interface QueueMessageRepository extends JpaRepository<QueueMessage, Long> {

    /**
     * Holt die ältesten offenen Nachrichten eines Themas und sperrt sie bis zum Ende der Transaktion.
     * SKIP LOCKED überspringt Zeilen, die eine andere Instanz gerade verarbeitet,
     * damit keine Nachricht doppelt verarbeitet wird. Nur innerhalb einer Transaktion aufrufen.
     */
    @Query(value = """
            SELECT * FROM queue_message
            WHERE topic = :topic AND failed = false
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<QueueMessage> lockNextBatch(@Param("topic") String topic, @Param("limit") int limit);
}