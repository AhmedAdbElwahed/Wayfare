package com.wayfare.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code driver.events} whenever a driver's eligibility to take
 * trips changes ({@code APPROVED} or {@code SUSPENDED}). One record with a
 * status field rather than a type per transition, so consumers of the topic
 * need no type discrimination on the wire. {@code reason} is only set for
 * suspensions (e.g. {@code document_expired}).
 */
public record DriverStatusChangedEvent(
        UUID driverId,
        String status,
        String reason,
        Instant occurredAt) {
}
