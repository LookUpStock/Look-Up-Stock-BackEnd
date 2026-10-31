package com.lookupstock.backend.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Nachricht in der Queue-Tabelle (BE #114, ADR-07).
 * Der Inhalt liegt als JSON-Text vor; Umwandlung übernehmen Publisher und Verbraucher.
 */
@Entity
@Table(name = "queue_message")
public class QueueMessage {

    // Höchstlänge der gespeicherten Fehlermeldung, wie in V10
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String topic;

    // JSONB-Spalte, wie bei der gespeicherten Simulation (V7)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean failed;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected QueueMessage() {
        // für JPA
    }

    public QueueMessage(String topic, String payload, Instant createdAt) {
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /**
     * Vermerkt einen fehlgeschlagenen Versuch. Ab maxAttempts gilt die Nachricht als fehlgeschlagen.
     */
    public void registerFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
        if (this.attempts >= maxAttempts) {
            this.failed = true;
        }
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }
    public boolean isFailed() { return failed; }
    public String getLastError() { return lastError; }
}