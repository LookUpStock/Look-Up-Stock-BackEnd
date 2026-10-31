package com.lookupstock.backend.queue;

import java.time.Instant;

import com.lookupstock.backend.model.UsageEventType;

/**
 * Inhalt einer Nachricht zum Thema nutzung.ereignis: nur Art und Zeitpunkt (E36).
 */
public record UsageEventMessage(UsageEventType type, Instant occurredAt) {
}