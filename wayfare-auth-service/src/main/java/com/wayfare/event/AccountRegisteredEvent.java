package com.wayfare.event;

import com.wayfare.domain.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the {@code account.events} topic the moment an account is
 * created. This is the contract other services code against — rider-service
 * has its own structurally identical copy rather than sharing a jar with this
 * module, which is deliberate: a shared event jar would make every consumer
 * redeploy in lockstep with this service, exactly the coupling the
 * choreography is meant to avoid. The wire format is JSON, so only the field
 * names matter, and adding a field here stays backward compatible.
 *
 * <p>{@code accountId} is the same UUID that ends up as the JWT {@code sub},
 * which is what lets rider-service key its profile row by it directly.
 */
public record AccountRegisteredEvent(
        UUID accountId,
        String email,
        AccountRole role,
        Instant registeredAt) {
}
