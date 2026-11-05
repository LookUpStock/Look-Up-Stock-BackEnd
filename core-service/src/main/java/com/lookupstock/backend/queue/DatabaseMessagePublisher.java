package com.lookupstock.backend.queue;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.model.QueueMessage;
import com.lookupstock.backend.repository.QueueMessageRepository;

import tools.jackson.databind.ObjectMapper;

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

    // Own transaction: a failure here must not mark the caller's transaction as rollback-only.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String topic, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        queueMessageRepository.save(new QueueMessage(topic, json, Instant.now(clock)));
    }
}