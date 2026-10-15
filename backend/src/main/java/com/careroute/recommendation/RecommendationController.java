package com.careroute.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final LiveHospitalService liveHospitalService;

    public RecommendationController(RecommendationService recommendationService, LiveHospitalService liveHospitalService) {
        this.recommendationService = recommendationService;
        this.liveHospitalService = liveHospitalService;
    }

    @GetMapping
    public List<RecommendationService.Recommendation> recommendations(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        boolean hasLocation = lat != null && lng != null;
        List<Hospital> hospitals = liveHospitalService.latest(
                hasLocation ? lat : 0.0,
                hasLocation ? lng : 0.0,
                hasLocation);
        return recommendationService.rank(hospitals);
    }
}
