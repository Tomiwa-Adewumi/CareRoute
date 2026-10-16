package com.careroute.recommendation;

import com.careroute.data.HospitalEntity;
import com.careroute.data.WaitTimeObservationEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitTimeForecastServiceTest {
    private final WaitTimeForecastService service = new WaitTimeForecastService();

    @Test
    void projectsRecentTrendToArrivalTime() {
        HospitalEntity hospital = new HospitalEntity("test", "Test", "Toronto", "ON", null, 43.6, -79.4, true, false, true);
        Instant now = Instant.parse("2026-10-16T16:00:00Z");
        var observations = List.of(
                observation(hospital, 60, now),
                observation(hospital, 50, now.minusSeconds(600)),
                observation(hospital, 40, now.minusSeconds(1200)));

        var forecast = service.predict(observations, 10);

        assertThat(forecast.predictedWaitMinutes()).isEqualTo(70);
        assertThat(forecast.sampleCount()).isEqualTo(3);
        assertThat(forecast.model()).isEqualTo("LINEAR_REGRESSION_V1");
    }

    private WaitTimeObservationEntity observation(HospitalEntity hospital, int wait, Instant observedAt) {
        return new WaitTimeObservationEntity(hospital, wait, observedAt, observedAt, "TEST", null);
    }
}
