package com.lookupstock.backend.queue;

/**
 * Themen der Queue (ADR-07).
 */
public final class QueueTopics {

    // Nutzungsereignisse für die Auswertung (US-11)
    public static final String USAGE_EVENT = "nutzung.ereignis";

    private QueueTopics() {
        // nur Konstanten
    }
}