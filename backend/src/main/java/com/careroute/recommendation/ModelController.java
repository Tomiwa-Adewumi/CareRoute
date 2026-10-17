package com.careroute.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
public class ModelController {
    private final ModelEvaluationService evaluationService;

    public ModelController(ModelEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/metrics")
    public ModelEvaluationService.Evaluation metrics() {
        return evaluationService.evaluate();
    }

    @GetMapping("/card")
    public Map<String, Object> modelCard() {
        return Map.of(
                "name", "Per-hospital wait-at-arrival forecaster",
                "version", "LINEAR_REGRESSION_V1",
                "type", "Ordinary least squares regression",
                "intendedUse", "Rank Toronto emergency departments by estimated total time to care",
                "notIntendedFor", List.of("diagnosis", "triage", "clinical decision-making"),
                "features", List.of("observation timestamp", "recent reported waits", "travel-time forecast horizon"),
                "guardrails", List.of("three-sample minimum", "current-wait fallback", "0–720 minute bounds", "±60 minute extrapolation cap"),
                "knownLimitations", List.of("no traffic routing", "no staffing or acuity features", "not clinically validated"));
    }
}
