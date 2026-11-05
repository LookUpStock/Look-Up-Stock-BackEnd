package com.lookupstock.backend.queue;

import java.time.Instant;

import com.lookupstock.backend.model.UsageEventType;

public record UsageEventMessage(UsageEventType type, Instant occurredAt) {
}