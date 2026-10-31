package com.lookupstock.backend.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lookupstock.backend.model.QueueMessage;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.repository.QueueMessageRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit-Tests für DatabaseMessagePublisher (BE #114): Nachricht wird mit Thema, JSON und Zeitpunkt gespeichert.
 */
class DatabaseMessagePublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:15:00Z");

    @Test
    void publishSavesMessageWithTopicJsonAndTime() {
        QueueMessageRepository repository = mock(QueueMessageRepository.class);
        JsonMapper mapper = JsonMapper.builder().build();
        DatabaseMessagePublisher publisher = new DatabaseMessagePublisher(
                repository, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

        publisher.publish(QueueTopics.USAGE_EVENT, new UsageEventMessage(UsageEventType.LOGIN, NOW));

        ArgumentCaptor<QueueMessage> captor = ArgumentCaptor.forClass(QueueMessage.class);
        verify(repository).save(captor.capture());
        QueueMessage saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("nutzung.ereignis");
        // Der gespeicherte JSON-Text ergibt wieder dieselbe Nachricht
        assertThat(mapper.readValue(saved.getPayload(), UsageEventMessage.class))
                .isEqualTo(new UsageEventMessage(UsageEventType.LOGIN, NOW));
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.isFailed()).isFalse();
    }
}