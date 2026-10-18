package com.careroute.recommendation;

import java.time.Instant;

public record Hospital(
        String id,
        String name,
        Integer waitMinutes,
        Integer predictedWaitMinutes,
        Integer predictionLowerMinutes,
        Integer predictionUpperMinutes,
        int forecastSampleCount,
        String forecastModel,
        Integer travelMinutes,
        Double travelDistanceKm,
        String travelSource,
        Double latitude,
        Double longitude,
        Instant observedAt,
        boolean adultEmergency,
        boolean pediatricEmergency,
        String unavailableReason,
        String timestampSource) {
}
