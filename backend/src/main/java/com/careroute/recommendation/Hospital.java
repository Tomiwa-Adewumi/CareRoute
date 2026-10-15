package com.careroute.recommendation;

import java.time.Instant;

public record Hospital(
        String id,
        String name,
        int waitMinutes,
        int travelMinutes,
        Instant observedAt,
        boolean adultEmergency,
        boolean pediatricEmergency) {
}
