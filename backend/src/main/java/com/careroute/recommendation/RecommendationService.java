package com.careroute.recommendation;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {

    public List<Recommendation> rank(List<Hospital> hospitals) {
        return hospitals.stream()
                .map(this::score)
                .sorted(Comparator.comparingDouble(Recommendation::score))
                .toList();
    }

    Recommendation score(Hospital hospital) {
        long ageMinutes = Math.max(0, Duration.between(hospital.observedAt(), Instant.now()).toMinutes());
        double freshnessPenalty = Math.min(ageMinutes, 120) * 0.20;
        double score = hospital.predictedWaitMinutes() + hospital.travelMinutes() + freshnessPenalty;

        String explanation = "%d min travel + %d min predicted wait%s".formatted(
                hospital.travelMinutes(),
                hospital.predictedWaitMinutes(),
                freshnessPenalty > 0 ? " + stale-data penalty" : "");

        return new Recommendation(hospital, Math.round(score * 10.0) / 10.0,
                new ScoreBreakdown(hospital.predictedWaitMinutes(), hospital.travelMinutes(),
                        Math.round(freshnessPenalty * 10.0) / 10.0), explanation);
    }

    public record ScoreBreakdown(int predictedWaitMinutes, int travelMinutes, double freshnessPenaltyMinutes) {}
    public record Recommendation(Hospital hospital, double score, ScoreBreakdown scoreBreakdown, String explanation) {}
}
