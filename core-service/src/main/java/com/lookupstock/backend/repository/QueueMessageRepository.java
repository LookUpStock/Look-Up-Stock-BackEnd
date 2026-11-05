package com.lookupstock.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lookupstock.backend.model.QueueMessage;

public interface QueueMessageRepository extends JpaRepository<QueueMessage, Long> {

    // SKIP LOCKED skips rows another instance is processing; call only inside a transaction.
    @Query(value = """
            SELECT * FROM queue_message
            WHERE topic = :topic AND failed = false
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<QueueMessage> lockNextBatch(@Param("topic") String topic, @Param("limit") int limit);
}