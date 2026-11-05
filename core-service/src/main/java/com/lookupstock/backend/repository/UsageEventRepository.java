package com.lookupstock.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lookupstock.backend.model.UsageEvent;
import com.lookupstock.backend.model.UsageEventType;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    interface EventTypeCount {
        UsageEventType getEventType();
        long getTotal();
    }

    @Query("""
            SELECT e.eventType AS eventType, COUNT(e) AS total
            FROM UsageEvent e
            WHERE e.occurredAt >= :from AND e.occurredAt < :to
            GROUP BY e.eventType
            """)
    List<EventTypeCount> countByTypeBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT MIN(e.occurredAt) FROM UsageEvent e")
    Optional<Instant> findEarliestOccurredAt();
}
