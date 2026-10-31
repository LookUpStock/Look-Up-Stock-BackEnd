package com.lookupstock.backend.queue;

/**
 * Stellt Nachrichten in die Queue ein, ohne auf ihre Verarbeitung zu warten (ADR-07).
 * Die Schnittstelle verbirgt, ob die Queue eine Tabelle oder ein Message Broker ist (Protected Variations).
 */
public interface MessagePublisher {

    /**
     * Stellt eine Nachricht ein. Der Inhalt wird als JSON gespeichert.
     * Wirft eine Ausnahme, wenn das Einstellen fehlschlägt; Aufrufer entscheiden, wie sie damit umgehen.
     */
    void publish(String topic, Object payload);
}