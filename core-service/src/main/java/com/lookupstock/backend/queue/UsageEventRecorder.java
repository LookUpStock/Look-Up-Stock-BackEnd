package com.lookupstock.backend.queue;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.UsageEventType;

/**
 * Erfasst Nutzungsereignisse über die Queue (US-11, ADR-07).
 * Wird an den acht Stellen aus E37 nach einer erfolgreichen Aktion aufgerufen (BE #110).
 * Schlägt das Einstellen fehl, wird nur protokolliert; die Aktion bleibt erfolgreich.
 */
@Component
public class UsageEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageEventRecorder.class);

    private final MessagePublisher messagePublisher;
    private final Clock clock;

    public UsageEventRecorder(MessagePublisher messagePublisher, Clock clock) {
        this.messagePublisher = messagePublisher;
        this.clock = clock;
    }

    public void record(UsageEventType type) {
        try {
            // Zeitpunkt der Aktion, nicht der späteren Verarbeitung
            messagePublisher.publish(QueueTopics.USAGE_EVENT, new UsageEventMessage(type, Instant.now(clock)));
        } catch (RuntimeException e) {
            log.warn("Nutzungsereignis {} konnte nicht in die Queue gestellt werden: {}", type, e.getMessage());
        }
    }
}