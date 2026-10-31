package com.lookupstock.backend.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lookupstock.backend.model.QueueMessage;
import com.lookupstock.backend.model.UsageEvent;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.repository.QueueMessageRepository;
import com.lookupstock.backend.repository.UsageEventRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit-Tests für UsageEventConsumer (BE #114): Übertragen in usage_event, Löschen nach Erfolg,
 * Fehlversuche zählen und nach drei Versuchen als fehlgeschlagen markieren (ADR-07).
 */
class UsageEventConsumerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-31T22:59:58Z");

    private QueueMessageRepository queueMessageRepository;
    private UsageEventRepository usageEventRepository;
    private UsageEventConsumer consumer;

    @BeforeEach
    void setUp() {
        queueMessageRepository = mock(QueueMessageRepository.class);
        usageEventRepository = mock(UsageEventRepository.class);
        consumer = new UsageEventConsumer(queueMessageRepository, usageEventRepository, JsonMapper.builder().build());
    }

    @Test
    void validMessageIsSavedAsUsageEventAndDeleted() {
        QueueMessage message = message("{\"type\":\"COMPARISON_RUN\",\"occurredAt\":\"2026-10-31T22:59:58Z\"}");
        when(queueMessageRepository.lockNextBatch(QueueTopics.USAGE_EVENT, UsageEventConsumer.BATCH_SIZE))
                .thenReturn(List.of(message));

        consumer.processBatch();

        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(UsageEventType.COMPARISON_RUN);
        // Zeitpunkt der Aktion aus der Nachricht, nicht der Verarbeitung
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(OCCURRED_AT);
        verify(queueMessageRepository).delete(message);
    }

    @Test
    void invalidMessageStaysInQueueWithOneAttempt() {
        QueueMessage message = message("kein JSON");
        when(queueMessageRepository.lockNextBatch(QueueTopics.USAGE_EVENT, UsageEventConsumer.BATCH_SIZE))
                .thenReturn(List.of(message));

        consumer.processBatch();

        verify(usageEventRepository, never()).save(any());
        verify(queueMessageRepository, never()).delete(any());
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.isFailed()).isFalse();
        assertThat(message.getLastError()).isNotBlank();
    }

    @Test
    void messageIsMarkedFailedAfterThreeAttempts() {
        QueueMessage message = message("{\"type\":\"LOGIN\"}");
        when(queueMessageRepository.lockNextBatch(QueueTopics.USAGE_EVENT, UsageEventConsumer.BATCH_SIZE))
                .thenReturn(List.of(message));

        consumer.processBatch();
        consumer.processBatch();
        consumer.processBatch();

        assertThat(message.getAttempts()).isEqualTo(UsageEventConsumer.MAX_ATTEMPTS);
        assertThat(message.isFailed()).isTrue();
        verify(usageEventRepository, never()).save(any());
    }

    @Test
    void oneFailingMessageDoesNotStopTheOthers() {
        QueueMessage broken = message("kein JSON");
        QueueMessage valid = message("{\"type\":\"LOGIN\",\"occurredAt\":\"2026-10-31T22:59:58Z\"}");
        when(queueMessageRepository.lockNextBatch(QueueTopics.USAGE_EVENT, UsageEventConsumer.BATCH_SIZE))
                .thenReturn(List.of(broken, valid));

        consumer.processBatch();

        verify(usageEventRepository).save(any(UsageEvent.class));
        verify(queueMessageRepository).delete(valid);
        assertThat(broken.getAttempts()).isEqualTo(1);
    }

    private static QueueMessage message(String payload) {
        return new QueueMessage(QueueTopics.USAGE_EVENT, payload, OCCURRED_AT);
    }
}