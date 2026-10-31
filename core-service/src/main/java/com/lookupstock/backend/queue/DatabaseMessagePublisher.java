package com.lookupstock.backend.queue;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.model.QueueMessage;
import com.lookupstock.backend.repository.QueueMessageRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * MessagePublisher auf Basis der Queue-Tabelle in PostgreSQL (ADR-07, Variante B2).
 */
@Component
public class DatabaseMessagePublisher implements MessagePublisher {

    private final QueueMessageRepository queueMessageRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DatabaseMessagePublisher(QueueMessageRepository queueMessageRepository,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.queueMessageRepository = queueMessageRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Eigene Transaktion (REQUIRES_NEW): Ein Fehler beim Einstellen darf die Transaktion
     * der eigentlichen Aktion nicht als fehlgeschlagen markieren.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String topic, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        queueMessageRepository.save(new QueueMessage(topic, json, Instant.now(clock)));
    }
}