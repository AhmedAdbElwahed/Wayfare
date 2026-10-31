package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.wayfare.domain.Candidate;

class CandidateScorerTest {

    CandidateScorer scorer = new CandidateScorer(TestProperties.defaults());

    @Test
    void closerBeatsFartherAtEqualRating() {
        assertThat(scorer.score(new Candidate("near", 0.5, 4.5)))
                .isGreaterThan(scorer.score(new Candidate("far", 3.0, 4.5)));
    }

    @Test
    void muchBetterRatingBeatsSlightlyCloser() {
        // 1.0 km vs 1.2 km is 2 vs 2.4 minutes; a full star outweighs that.
        assertThat(scorer.score(new Candidate("rated", 1.2, 5.0)))
                .isGreaterThan(scorer.score(new Candidate("closer", 1.0, 4.0)));
    }

    @Test
    void missingRatingUsesTheDefault() {
        assertThat(scorer.score(new Candidate("unrated", 1.0, null)))
                .isEqualTo(scorer.score(new Candidate("default", 1.0, 4.5)));
    }

    @Test
    void driverAtThePickupHasAFiniteScore() {
        assertThat(scorer.score(new Candidate("here", 0.0, 4.5))).isFinite();
    }

    @Test
    void weightsChangeTheWinner() {
        Candidate close = new Candidate("close", 0.5, 3.5);
        Candidate rated = new Candidate("rated", 2.0, 5.0);
        CandidateScorer etaOnly = new CandidateScorer(TestProperties.weights(1.0, 0.0));
        CandidateScorer ratingOnly = new CandidateScorer(TestProperties.weights(0.0, 1.0));

        assertThat(etaOnly.score(close)).isGreaterThan(etaOnly.score(rated));
        assertThat(ratingOnly.score(rated)).isGreaterThan(ratingOnly.score(close));
    }
}
