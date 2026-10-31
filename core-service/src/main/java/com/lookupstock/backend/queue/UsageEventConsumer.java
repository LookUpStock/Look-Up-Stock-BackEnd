package com.lookupstock.backend.queue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.model.QueueMessage;
import com.lookupstock.backend.model.UsageEvent;
import com.lookupstock.backend.repository.QueueMessageRepository;
import com.lookupstock.backend.repository.UsageEventRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Verbraucher für das Thema nutzung.ereignis (US-11, ADR-07).
 * Holt alle 5 Sekunden höchstens 50 Nachrichten ab und überträgt sie in die Tabelle usage_event.
 */
@Component
public class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);

    // Höchstzahl Nachrichten je Durchlauf (ADR-07)
    static final int BATCH_SIZE = 50;

    // Nach so vielen Fehlversuchen wird eine Nachricht nicht weiter versucht (ADR-07)
    static final int MAX_ATTEMPTS = 3;

    private final QueueMessageRepository queueMessageRepository;
    private final UsageEventRepository usageEventRepository;
    private final ObjectMapper objectMapper;

    public UsageEventConsumer(QueueMessageRepository queueMessageRepository,
                              UsageEventRepository usageEventRepository,
                              ObjectMapper objectMapper) {
        this.queueMessageRepository = queueMessageRepository;
        this.usageEventRepository = usageEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ein Durchlauf in einer Transaktion: Die Sperre aus lockNextBatch gilt bis zum Ende der Methode.
     * fixedDelay: Der nächste Durchlauf startet 5 s nach dem Ende des vorherigen.
     */
    @Scheduled(fixedDelayString = "${app.queue.poll-interval-ms:5000}")
    @Transactional
    public void processBatch() {
        List<QueueMessage> messages = queueMessageRepository.lockNextBatch(QueueTopics.USAGE_EVENT, BATCH_SIZE);
        for (QueueMessage message : messages) {
            process(message);
        }
    }

    private void process(QueueMessage message) {
        try {
            UsageEventMessage content = objectMapper.readValue(message.getPayload(), UsageEventMessage.class);
            if (content.type() == null || content.occurredAt() == null) {
                throw new IllegalArgumentException("Art oder Zeitpunkt fehlt");
            }
            usageEventRepository.save(new UsageEvent(content.type(), content.occurredAt()));
            queueMessageRepository.delete(message);
        } catch (RuntimeException e) {
            // Nachricht bleibt in der Queue; nach MAX_ATTEMPTS Versuchen wird sie als fehlgeschlagen markiert
            message.registerFailure(e.getMessage(), MAX_ATTEMPTS);
            log.warn("Queue-Nachricht {} konnte nicht verarbeitet werden (Versuch {}): {}",
                    message.getId(), message.getAttempts(), e.getMessage());
        }
    }
}