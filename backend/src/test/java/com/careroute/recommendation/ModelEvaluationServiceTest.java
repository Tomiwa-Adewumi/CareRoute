package com.careroute.recommendation;

import com.careroute.data.HospitalEntity;
import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationEntity;
import com.careroute.data.WaitTimeObservationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelEvaluationServiceTest {
    @Test
    void usesRollingOriginAndReportsBaselinesWithoutFutureLeakage() {
        HospitalRepository hospitals = mock(HospitalRepository.class);
        WaitTimeObservationRepository observations = mock(WaitTimeObservationRepository.class);
        var hospital = new HospitalEntity("h1", "Test Hospital", "Toronto", "ON", null,
                43.65, -79.38, true, false, true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<WaitTimeObservationEntity> series = List.of(
                observation(hospital, 40, start), observation(hospital, 50, start.plusSeconds(600)),
                observation(hospital, 60, start.plusSeconds(1200)), observation(hospital, 70, start.plusSeconds(1800)));
        when(hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")).thenReturn(List.of(hospital));
        when(observations.findByHospital_IdOrderByObservedAtAsc("h1")).thenReturn(series);

        var result = new ModelEvaluationService(hospitals, observations, new WaitTimeForecastService()).evaluate();

        assertEquals("COLLECTING_HISTORY", result.status());
        assertEquals(1, result.evaluatedPredictions());
        assertNotNull(result.linearRegression());
        assertEquals(0.0, result.linearRegression().maeMinutes());
        assertEquals(10.0, result.latestWaitBaseline().maeMinutes());
    }

    private WaitTimeObservationEntity observation(HospitalEntity hospital, int wait, Instant at) {
        return new WaitTimeObservationEntity(hospital, wait, at, at, "TEST", "https://example.com");
    }
}
