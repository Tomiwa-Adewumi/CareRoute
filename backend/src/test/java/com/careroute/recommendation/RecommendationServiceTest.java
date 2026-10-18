package com.careroute.recommendation;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class RecommendationServiceTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final RecommendationService service = new RecommendationService(Clock.fixed(now, ZoneOffset.UTC));
    @Test void ranksLowerReportedTimeFirst() {
        var ranked = service.rank(List.of(hospital("slow", 80, now), hospital("fast", 40, now)));
        assertThat(ranked.get(0).hospital().id()).isEqualTo("fast");
        assertThat(ranked.get(0).score()).isEqualTo(45);
    }
    @Test void excludesDayOldLowWaitRatherThanLettingItWin() {
        var ranked = service.rank(List.of(hospital("old", 10, now.minusSeconds(86400)), hospital("fresh", 100, now)));
        assertThat(ranked.get(0).hospital().id()).isEqualTo("fresh");
        assertThat(ranked.get(1).score()).isNull();
        assertThat(ranked.get(1).status()).isEqualTo("UNAVAILABLE");
    }
    @Test void appliesPenaltyWithinFreshnessWindow() {
        var score = service.rank(List.of(hospital("h", 40, now.minusSeconds(600)))).get(0);
        assertThat(score.scoreBreakdown().freshnessPenaltyMinutes()).isEqualTo(2);
        assertThat(score.score()).isEqualTo(47);
    }
    private Hospital hospital(String id, int wait, Instant at) {
        return new Hospital(id, id, wait, wait, null, null, 1, "LATEST_WAIT_BASELINE", 5, 2.0,
                "OSRM_ROUTE", 43.65, -79.38, at, true, false, null, "ER_COMPASS_OBSERVED");
    }
}
