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
                "name", "Reported-wait comparison baseline",
                "version", "LATEST_WAIT_BASELINE",
                "type", "Latest reported wait; regression is an offline challenger",
                "comparisonModel", "EXPONENTIAL_SMOOTHING_V1 (alpha=0.5)",
                "intendedUse", "Compare available adult emergency-department reported waits and estimated travel",
                "notIntendedFor", List.of("diagnosis", "triage", "clinical decision-making"),
                "features", List.of("latest reported wait", "data age", "optional travel estimate"),
                "guardrails", List.of("30-minute freshness limit", "verified open adult emergency facilities", "unknown travel excluded from personalized ranking", "unavailable facilities shown without scores"),
                "knownLimitations", List.of("no traffic routing", "no staffing or acuity features", "not clinically validated"));
    }
}
