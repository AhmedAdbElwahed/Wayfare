package com.wayfare.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wayfare.config.MatchingProperties;
import com.wayfare.domain.ApprovedDriver;
import com.wayfare.domain.Candidate;
import com.wayfare.domain.NearbyDriver;
import com.wayfare.event.MatchEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispatches one ride request: search an expanding radius, keep APPROVED drivers,
 * rank them, reserve the best one that is still free, publish the outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final MatchingProperties properties;
    private final CandidateFinder finder;
    private final DriverDirectory directory;
    private final CandidateScorer scorer;
    private final DriverReservation reservation;
    private final DispatchGuard guard;
    private final MatchEventPublisher publisher;

    public void dispatch(String tripId, double pickupLat, double pickupLng) {
        if (!guard.tryBegin(tripId)) {
            log.info("Trip {} is already being dispatched; skipping duplicate", tripId);
            return;
        }
        try {
            Optional<String> driverId = reserveBest(tripId, pickupLat, pickupLng);
            if (driverId.isEmpty()) {
                // Final answer: the guard stays, so a redelivery doesn't search again.
                log.info("No driver available for trip {}", tripId);
                publisher.noDriversAvailable(tripId);
                return;
            }
            try {
                publisher.driverMatched(tripId, driverId.get());
            } catch (RuntimeException e) {
                // Trip Service never heard of this match; free the driver for others.
                reservation.release(driverId.get(), tripId);
                throw e;
            }
            log.info("Matched trip {} to driver {}", tripId, driverId.get());
        } catch (RuntimeException e) {
            // Let Kafka's redelivery dispatch again.
            guard.release(tripId);
            throw e;
        }
    }

    private Optional<String> reserveBest(String tripId, double lat, double lng) {
        Set<String> tried = new HashSet<>();
        for (double radiusKm : properties.radiiKm()) {
            List<NearbyDriver> hits = finder.find(properties.city(), lat, lng, radiusKm, properties.candidateLimit())
                    .stream()
                    .filter(hit -> !tried.contains(hit.driverId()))
                    .toList();
            if (hits.isEmpty()) {
                continue;
            }
            hits.forEach(hit -> tried.add(hit.driverId()));

            Map<String, ApprovedDriver> approved = directory.approved(hits.stream().map(NearbyDriver::driverId).toList());
            List<Candidate> ranked = hits.stream()
                    .filter(hit -> approved.containsKey(hit.driverId()))
                    .map(hit -> new Candidate(hit.driverId(), hit.distanceKm(), approved.get(hit.driverId()).ratingAvg()))
                    .sorted(Comparator.comparingDouble(scorer::score).reversed())
                    .toList();

            for (Candidate c : ranked) {
                // A false here means another trip reserved this driver first.
                if (reservation.tryReserve(c.driverId(), tripId)) {
                    return Optional.of(c.driverId());
                }
            }
        }
        return Optional.empty();
    }
}
