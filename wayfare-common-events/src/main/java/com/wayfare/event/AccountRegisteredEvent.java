package com.wayfare.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the {@code account.events} topic the moment an account is
 * created. Single definition shared by the producer (auth-service) and its
 * consumers, so the wire contract can't drift between copies.
 *
 * <p>Evolve it additively only: the wire format is JSON, so adding a field is
 * backward compatible while renaming or removing one breaks every consumer
 * still on an older release.
 *
 * <p>{@code accountId} is the same UUID that ends up as the JWT {@code sub},
 * which is what lets rider-service key its profile row by it directly.
 * {@code role} is a plain String (the {@code AccountRole} name, e.g.
 * {@code RIDER}) rather than an enum: an unrecognised role from a future
 * auth-service release should be ignored by a consumer, not blow up
 * deserialization for every message on the topic.
 */
public record AccountRegisteredEvent(
        UUID accountId,
        String email,
        String role,
        Instant registeredAt) {
}
