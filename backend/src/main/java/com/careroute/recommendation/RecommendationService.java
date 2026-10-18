package com.careroute.recommendation;

import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {
    private final Clock clock;
    public RecommendationService(Clock clock) { this.clock = clock; }
    public List<Recommendation> rank(List<Hospital> hospitals) {
        return hospitals.stream().map(this::score)
                .sorted(Comparator.comparing(Recommendation::score, Comparator.nullsLast(Double::compareTo)))
                .toList();
    }
    Recommendation score(Hospital hospital) {
        String reason = hospital.unavailableReason();
        if (reason == null && (hospital.observedAt() == null || hospital.waitMinutes() == null)) reason = "Current wait is unavailable";
        if (reason == null && Duration.between(hospital.observedAt(), clock.instant()).compareTo(Duration.ofMinutes(30)) > 0) reason = "Data is more than 30 minutes old";
        if (reason != null) return new Recommendation(hospital, null, null, reason, "UNAVAILABLE");
        long age = Math.max(0, Duration.between(hospital.observedAt(), clock.instant()).toMinutes());
        double penalty = Math.round(age * 0.20 * 10) / 10.0;
        int travel = hospital.travelMinutes() == null ? 0 : hospital.travelMinutes();
        double score = Math.round((hospital.waitMinutes() + travel + penalty) * 10) / 10.0;
        String explanation = "%d min reported wait + %d min travel + %.1f freshness penalty".formatted(hospital.waitMinutes(), travel, penalty);
        return new Recommendation(hospital, score, new ScoreBreakdown(hospital.waitMinutes(), travel, penalty), explanation, "AVAILABLE");
    }
    public record ScoreBreakdown(int predictedWaitMinutes, int travelMinutes, double freshnessPenaltyMinutes) {}
    public record Recommendation(Hospital hospital, Double score, ScoreBreakdown scoreBreakdown, String explanation, String status) {}
}
