package com.wayfare.event;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wayfare.service.DriverReservation;
import com.wayfare.service.MatchingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads every event on {@code trip.events}. The bean name {@code onTripEvent}
 * ties it to {@code onTripEvent-in-0}.
 *
 * <ul>
 *   <li>RideRequested: dispatch.</li>
 *   <li>TripCompleted / TripCancelled: free the driver before the reservation TTL.</li>
 *   <li>Anything else: ignored.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TripEventConsumer {

    private final MatchingService matching;
    private final DriverReservation reservation;

    @Bean
    public Consumer<TripEvent> onTripEvent() {
        return event -> {
            switch (event.getEventCase()) {
                case RIDE_REQUESTED -> {
                    LatLng pickup = event.getRideRequested().getPickup();
                    matching.dispatch(event.getTripId(), pickup.getLat(), pickup.getLng());
                }
                case TRIP_COMPLETED, TRIP_CANCELLED -> {
                    log.debug("Trip {} ended ({}); releasing its driver", event.getTripId(), event.getEventCase());
                    reservation.releaseTrip(event.getTripId());
                }
                default -> {
                }
            }
        };
    }
}
