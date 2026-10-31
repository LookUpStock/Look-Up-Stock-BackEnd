package com.lookupstock.backend.queue;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lookupstock.backend.model.UsageEventType;

/**
 * Unit-Tests für UsageEventRecorder (BE #114): Ereignis wird eingestellt,
 * ein Ausfall der Queue lässt die Aktion nicht scheitern (ADR-07).
 */
class UsageEventRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:15:00Z");

    private MessagePublisher publisher;
    private UsageEventRecorder recorder;

    @BeforeEach
    void setUp() {
        publisher = mock(MessagePublisher.class);
        recorder = new UsageEventRecorder(publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordPublishesTypeAndCurrentTime() {
        recorder.record(UsageEventType.SIMULATION_RUN);

        verify(publisher).publish(QueueTopics.USAGE_EVENT,
                new UsageEventMessage(UsageEventType.SIMULATION_RUN, NOW));
    }

    @Test
    void recordDoesNotThrowWhenQueueFails() {
        doThrow(new IllegalStateException("Datenbank nicht erreichbar"))
                .when(publisher).publish(anyString(), any());

        assertThatCode(() -> recorder.record(UsageEventType.LOGIN)).doesNotThrowAnyException();
    }
}