package com.wayfare.service;

import org.springframework.stereotype.Component;

import com.wayfare.config.MatchingProperties;
import com.wayfare.domain.Candidate;

import lombok.RequiredArgsConstructor;

/**
 * Pure ranking function: higher is better. Closer drivers (lower ETA) and better
 * ratings both raise the score; the weights come from config.
 */
@Component
@RequiredArgsConstructor
public class CandidateScorer {

    private final MatchingProperties properties;

    public double score(Candidate candidate) {
        double etaMinutes = candidate.distanceKm() / properties.avgSpeedKmh() * 60;
        double rating = candidate.averageRating() != null
                ? candidate.averageRating()
                : properties.defaultRating();
        // +0.1 keeps a driver standing at the pickup (ETA 0) from dividing by zero.
        return properties.etaWeight() * (1 / (etaMinutes + 0.1))
                + properties.ratingWeight() * rating;
    }
}
