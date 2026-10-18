package com.careroute.recommendation;

import com.careroute.data.HospitalEntity;
import com.careroute.data.WaitTimeObservationEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExponentialSmoothingForecastServiceTest {
    @Test
    void weightsRecentWaitsMoreHeavily() {
        var hospital = new HospitalEntity("h1", "Test Hospital", "Toronto", "ON", null,
                43.65, -79.38, true, false, true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<WaitTimeObservationEntity> newestFirst = List.of(
                observation(hospital, 60, start.plusSeconds(1200)),
                observation(hospital, 50, start.plusSeconds(600)),
                observation(hospital, 40, start));

        var forecast = new ExponentialSmoothingForecastService().predict(newestFirst);

        assertEquals(53, forecast.predictedWaitMinutes());
        assertEquals("EXPONENTIAL_SMOOTHING_V1", forecast.model());
    }

    private WaitTimeObservationEntity observation(HospitalEntity hospital, int wait, Instant at) {
        return new WaitTimeObservationEntity(hospital, wait, at, at, "TEST", "https://example.com");
    }
}
