package com.careroute.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<RecommendationService.Recommendation> recommendations() {
        // MVP seed data. The ingestion layer will replace this with live public hospital sources.
        List<Hospital> hospitals = List.of(
                new Hospital("uhn-tgh", "Toronto General Hospital", 118, 16, Instant.now().minusSeconds(8 * 60), true, false),
                new Hospital("st-michaels", "St. Michael's Hospital", 92, 24, Instant.now().minusSeconds(22 * 60), true, false),
                new Hospital("sunnybrook", "Sunnybrook Health Sciences Centre", 74, 34, Instant.now().minusSeconds(5 * 60), true, false)
        );

        return recommendationService.rank(hospitals);
    }
}
