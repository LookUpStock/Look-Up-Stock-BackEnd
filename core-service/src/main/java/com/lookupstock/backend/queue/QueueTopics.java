package com.lookupstock.backend.queue;

/**
 * Themen der Queue (ADR-07).
 */
public final class QueueTopics {

    // Nutzungsereignisse für die Auswertung (US-11)
    public static final String USAGE_EVENT = "nutzung.ereignis";

    // Kursdaten der Anlagen eines Portfolios im marktdaten-service vorladen (NF-07, ADR-07)
    public static final String PRICE_PRELOAD = "kursdaten.vorladen";

    private QueueTopics() {
        // nur Konstanten
    }
}