package com.careroute.recommendation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

    private final RecommendationService service = new RecommendationService();

    @Test
    void ranksLowerExpectedTimeToCareFirst() {
        Instant now = Instant.now();
        Hospital faster = hospital("a", "Faster", 60, 10, now);
        Hospital slower = hospital("b", "Slower", 80, 15, now);

        var ranked = service.rank(List.of(slower, faster));

        assertThat(ranked.get(0).hospital().id()).isEqualTo("a");
    }

    @Test
    void staleDataReceivesPenalty() {
        Instant now = Instant.now();
        Hospital fresh = hospital("fresh", "Fresh", 60, 10, now);
        Hospital stale = hospital("stale", "Stale", 60, 10, now.minusSeconds(60 * 60));

        var ranked = service.rank(List.of(stale, fresh));

        assertThat(ranked.get(0).hospital().id()).isEqualTo("fresh");
        assertThat(ranked.get(1).score()).isGreaterThan(ranked.get(0).score());
    }

    private Hospital hospital(String id, String name, int wait, int travel, Instant observedAt) {
        return new Hospital(id, name, wait, wait, wait - 10, wait + 10, 4,
                "LINEAR_REGRESSION_V1", travel, 5.0, "TEST", 43.65, -79.38,
                observedAt, true, false);
    }
}
