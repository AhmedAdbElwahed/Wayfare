package com.wayfare.event;

import com.wayfare.domain.IllegalStateTransitionException;
import com.wayfare.service.TripCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Listens to the matching service's outcome on {@code match.events} (protobuf
 * {@code MatchEvent}, schema in wayfare-common-events) and turns it into trip
 * commands. The bean name {@code onMatchEvent} ties it to {@code onMatchEvent-in-0}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MatchEventsConsumer {

    private final TripCommandService commands;

    @Bean
    public Consumer<MatchEvent> onMatchEvent() {
        return event -> {
            UUID tripId = UUID.fromString(event.getTripId());
            try {
                switch (event.getOutcomeCase()) {
                    case DRIVER_MATCHED -> commands.applyDriverMatched(
                            tripId, UUID.fromString(event.getDriverMatched().getDriverId()));
                    case NO_DRIVERS_AVAILABLE -> commands.applyNoDriversFound(tripId);
                    case OUTCOME_NOT_SET -> log.debug("Ignoring match event with no known outcome");
                }
            } catch (IllegalStateTransitionException ex) {
                // A redelivered message finds the trip already moved on. Retrying can
                // never succeed, so drop it instead of dead-lettering a harmless duplicate.
                log.info("Skipping match event for trip {}: {}", tripId, ex.getMessage());
            }
        };
    }
}
