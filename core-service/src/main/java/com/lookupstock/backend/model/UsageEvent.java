package com.lookupstock.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usage_event")
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private UsageEventType eventType;

    // Time of the action, not of queue processing.
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected UsageEvent() {
    }

    public UsageEvent(UsageEventType eventType, Instant occurredAt) {
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UsageEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
}