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
        Hospital faster = new Hospital("a", "Faster", 60, 60, 50, 70, 4, "LINEAR_REGRESSION_V1", 10, now, true, false);
        Hospital slower = new Hospital("b", "Slower", 80, 80, 70, 90, 4, "LINEAR_REGRESSION_V1", 15, now, true, false);

        var ranked = service.rank(List.of(slower, faster));

        assertThat(ranked.get(0).hospital().id()).isEqualTo("a");
    }

    @Test
    void staleDataReceivesPenalty() {
        Instant now = Instant.now();
        Hospital fresh = new Hospital("fresh", "Fresh", 60, 60, 50, 70, 4, "LINEAR_REGRESSION_V1", 10, now, true, false);
        Hospital stale = new Hospital("stale", "Stale", 60, 60, 50, 70, 4, "LINEAR_REGRESSION_V1", 10, now.minusSeconds(60 * 60), true, false);

        var ranked = service.rank(List.of(stale, fresh));

        assertThat(ranked.get(0).hospital().id()).isEqualTo("fresh");
        assertThat(ranked.get(1).score()).isGreaterThan(ranked.get(0).score());
    }
}
