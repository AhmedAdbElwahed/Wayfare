package com.wayfare.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Structural copy of the event wayfare-auth-service publishes to
 * {@code account.events}. Deliberately not a shared jar — see the publisher
 * side for the reasoning — so this record only has to stay compatible with
 * the JSON on the wire, not identical to the producer's class.
 *
 * <p>{@code role} is a plain String rather than an enum: this service has no
 * {@code AccountRole} of its own, and an unrecognised role from a future
 * auth-service release should be ignored by {@link AccountEventsConsumer},
 * not blow up deserialization for every message on the topic.
 */
public record AccountRegisteredEvent(
        UUID accountId,
        String email,
        String role,
        Instant registeredAt) {
}
