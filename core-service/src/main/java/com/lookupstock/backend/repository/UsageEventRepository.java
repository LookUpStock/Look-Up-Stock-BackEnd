package com.lookupstock.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lookupstock.backend.model.UsageEvent;
import com.lookupstock.backend.model.UsageEventType;

/**
 * Zugriff auf die Nutzungsereignisse (US-11).
 */
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    /** Anzahl je Ereignisart in einem Zeitraum (Ergebnis einer Gruppierung) */
    interface EventTypeCount {
        UsageEventType getEventType();
        long getTotal();
    }

    /**
     * Zählt die Ereignisse je Art im halboffenen Zeitraum [from, to) (BE #111).
     * Arten ohne Ereignis fehlen im Ergebnis.
     */
    @Query("""
            SELECT e.eventType AS eventType, COUNT(e) AS total
            FROM UsageEvent e
            WHERE e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.eventType
            """)
    List<EventTypeCount> countByTypeBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Zeitpunkt des ersten erfassten Ereignisses = Beginn der Erfassung (E38); leer, solange es keins gibt */
    @Query("SELECT MIN(e.occurredAt) FROM UsageEvent e")
    Optional<Instant> findEarliestOccurredAt();
}
